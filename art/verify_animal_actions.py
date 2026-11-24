"""Exercise the current ROM's published select/grab/release actions without moving pieces."""
import json
import os
from pathlib import Path
import subprocess

env = dict(os.environ, PICO_CLI_NO_TELEMETRY="1")
package = "com.pico.junglechess"
def cli(args):
    result = subprocess.run(["pico-cli", *args], env=env, text=True, capture_output=True)
    result.check_returncode()
    return json.loads(result.stdout)

evidence = []
ui = cli(["interact","query","--package",package,"--include-hidden","--format","json"])
for label in ["鼠","猫","狗","狼","豹","虎","狮","象"]:
    for action in ["选择","抓起","放下"]:
        node = next(c for c in ui.get("controls",[]) if c.get("name") == "红方"+label)
        published = next(a for a in node.get("actions",[]) if a.get("label",a.get("key"))==action+label)
        result = cli(["interact","perform","--package",package,"--node",node["node"],
                      "--action-id",str(published["id"]),"--format","json"])
        assert result.get("performed") is True, result
        evidence.append({"animal":label,"action":action,"result":result})
        Path("/tmp/jungle110-piece-actions.json").write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
        print(label,action,json.dumps(result,ensure_ascii=False))
Path("/tmp/jungle110-piece-actions.json").write_text(json.dumps(evidence,ensure_ascii=False,indent=2))
