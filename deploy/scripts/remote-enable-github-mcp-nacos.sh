#!/usr/bin/env bash
set -euo pipefail
APP_DIR=/opt/dream-agentscope
cd "$APP_DIR"
cleaned=$(mktemp /tmp/agentscope-runenv.XXXXXX)
python3 - "${APP_DIR}/run.env" "${cleaned}" <<'PY'
import shlex, sys
from pathlib import Path
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
raw = src.read_bytes()
text = "\n".join(c.decode("utf-8","replace") for c in raw.split(b"\x00")) if b"\x00" in raw else raw.decode("utf-8","replace")
exports=[]
for line in text.splitlines():
    line=line.strip().lstrip("\ufeff")
    if not line or line.startswith("#"): continue
    if line.startswith("export "): line=line[7:].lstrip()
    if "=" not in line: continue
    k,v=line.split("=",1); k=k.strip()
    if k.isidentifier(): exports.append(f"export {k}={shlex.quote(v)}")
dst.write_text("\n".join(exports)+"\n", encoding="utf-8")
print(f"run.env prepared exports={len(exports)}")
PY
set -a; . "${cleaned}"; set +a; rm -f "${cleaned}"
: "${NACOS_ADDR:?}"; : "${NACOS_NAMESPACE:?}"
export NACOS_NAMESPACE NACOS_USERNAME NACOS_PASSWORD
python3 - <<'PY'
import json, os, re, urllib.parse, urllib.request
ns=os.environ["NACOS_NAMESPACE"]
user=os.environ["NACOS_USERNAME"]; pwd=os.environ["NACOS_PASSWORD"]
base="http://127.0.0.1:8848"
body=urllib.parse.urlencode({"username":user,"password":pwd}).encode()
with urllib.request.urlopen(urllib.request.Request(base+"/nacos/v1/auth/login", data=body, method="POST"), timeout=15) as r:
    token=json.loads(r.read().decode())["accessToken"]

def api(method, path, params=None, form=None):
    headers={"Authorization": f"Bearer {token}", "accessToken": token}
    url=base+path
    if params: url += "?" + urllib.parse.urlencode(params)
    data=None
    if form is not None:
        data=urllib.parse.urlencode(form).encode()
        headers["Content-Type"]="application/x-www-form-urlencoded"
    req=urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.status, resp.read().decode()

st, raw = api("GET", "/nacos/v3/admin/cs/config", params={
    "dataId":"agent-bootstrap.yaml","groupName":"AGENT_PLATFORM","namespaceId":ns,"accessToken":token
})
j=json.loads(raw)
content=(j.get("data") or {}).get("content") or ""
if not content.strip():
    raise SystemExit("EMPTY_CONFIG")
print("CONTENT_LEN", len(content), "HAS_GITHUB_MCP", int("github-mcp-server" in content))

GITHUB_BLOCK = """    mcp:
      servers:
        github:
          transport: stdio
          command: /opt/dream-agentscope/mcp/github-mcp-server
          args:
            - stdio
            - --read-only
            - --toolsets
            - repos
          env:
            GITHUB_PERSONAL_ACCESS_TOKEN: ${GITHUB_TOKEN}
          required: false
"""

if re.search(r"(?m)^\s+github:\s*$", content) and "github-mcp-server" in content:
    print("NACOS_GITHUB_MCP_ALREADY=1"); print("NACOS_CHANGED=0"); raise SystemExit(0)

if re.search(r"(?m)^\s+mcp:\s*$", content):
    if re.search(r"(?m)^\s+servers:\s*$", content):
        new = re.sub(r"(?m)^(\s+servers:\s*\n)",
            r"\1        github:\n          transport: stdio\n          command: /opt/dream-agentscope/mcp/github-mcp-server\n          args:\n            - stdio\n            - --read-only\n            - --toolsets\n            - repos\n          env:\n            GITHUB_PERSONAL_ACCESS_TOKEN: ${GITHUB_TOKEN}\n          required: false\n",
            content, count=1)
    else:
        new = re.sub(r"(?m)^(\s+mcp:\s*\n)",
            r"\1      servers:\n        github:\n          transport: stdio\n          command: /opt/dream-agentscope/mcp/github-mcp-server\n          args:\n            - stdio\n            - --read-only\n            - --toolsets\n            - repos\n          env:\n            GITHUB_PERSONAL_ACCESS_TOKEN: ${GITHUB_TOKEN}\n          required: false\n",
            content, count=1)
elif "web-tools:" in content:
    new = re.sub(r"(?m)^(    web-tools:\s*\n(?:[ \t]+.+\n)*)", r"\1"+GITHUB_BLOCK, content, count=1)
elif re.search(r"(?m)^  runtime:\s*$", content) or re.search(r"(?m)^  runtime:\s*\n", content):
    new = re.sub(r"(?m)^(  runtime:\s*\n)", r"\1"+GITHUB_BLOCK, content, count=1)
else:
    new = content.rstrip() + "\n  runtime:\n" + GITHUB_BLOCK

if new == content:
    raise SystemExit("PATCH_NOOP_FAIL")

# publish via v3 admin POST
form={
    "dataId":"agent-bootstrap.yaml",
    "groupName":"AGENT_PLATFORM",
    "namespaceId":ns,
    "type":"yaml",
    "content":new,
    "accessToken":token,
}
# try POST then PUT
ok=False
for method in ("POST","PUT"):
    try:
        st, raw = api(method, "/nacos/v3/admin/cs/config", form=form)
        print("PUBLISH", method, "HTTP", st, "LEN", len(raw))
        try:
            pj=json.loads(raw); print("PUBLISH_CODE", pj.get("code"), "MSG", str(pj.get("message"))[:80], "DATA", str(pj.get("data"))[:80])
            if pj.get("code") in (0, 200, None) or pj.get("data") in (True, "true", "True"):
                ok=True; break
        except Exception:
            print("PUBLISH_RAW_PREFIX", raw[:60]); ok = st==200; break
    except Exception as e:
        eb=""
        if hasattr(e,"read"):
            try: eb=e.read().decode()[:200]
            except Exception: pass
        print("PUBLISH", method, "ERR", getattr(e,"code",type(e).__name__), eb or str(e)[:120])

if not ok:
    raise SystemExit("PUBLISH_FAIL")

# verify
st, raw = api("GET", "/nacos/v3/admin/cs/config", params={
    "dataId":"agent-bootstrap.yaml","groupName":"AGENT_PLATFORM","namespaceId":ns,"accessToken":token
})
c=(json.loads(raw).get("data") or {}).get("content") or ""
print("NACOS_CHANGED=1")
print("NACOS_GITHUB_MCP_ENABLED="+("1" if "github-mcp-server" in c and "${GITHUB_TOKEN}" in c else "0"))
print("VERIFY_LEN", len(c))
PY

# token key presence only
python3 - <<'PY'
from pathlib import Path
raw=Path("/opt/dream-agentscope/run.env").read_bytes()
text="\n".join(c.decode("utf-8","replace") for c in raw.split(b"\x00")) if b"\x00" in raw else raw.decode("utf-8","replace")
has=False
for line in text.splitlines():
    line=line.strip()
    if line.startswith("export "): line=line[7:].lstrip()
    if line.startswith("GITHUB_TOKEN=") and line.split("=",1)[1].strip():
        has=True
print("GITHUB_TOKEN_PRESENT="+("1" if has else "0"))
PY