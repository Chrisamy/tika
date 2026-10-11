"""Generate an API reference of the collaborator types of a focal class for the ChatUniTest prompts.

The reference lists, from the compiled classes (javap -public), the public API of every project type
that appears in the public signatures of the focal class, plus nested types those reference
(e.g. builders). initial.ftl / repair.ftl include it as api/<SimpleName>.txt so the model only uses
methods that really exist.

Usage (from the repository root, after compiling the module):
    python tika-serialization/chatunitest-prompts/gen_api_ref.py org.apache.tika.serialization.ComponentNameResolver
"""
import os
import re
import subprocess
import sys
import tempfile

MODULE = "tika-serialization"
PROJECT_TYPE = re.compile(r"org\.apache\.tika\.[\w.$]+")


def build_classpath():
    out = os.path.join(tempfile.gettempdir(), "chatunitest-cp.txt")
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    mvnw = os.path.join(root, "mvnw.cmd" if os.name == "nt" else "mvnw")
    subprocess.run([mvnw, "-q", "-o", "-pl", MODULE, "dependency:build-classpath",
                    "-Dmdep.includeScope=test", "-Dmdep.outputFile=" + out,
                    "-Dcheckstyle.skip", "-Dspotless.apply.skip"], check=True, cwd=root)
    with open(out, encoding="utf8") as f:
        deps = f.read().strip()
    return os.pathsep.join([os.path.join(MODULE, "target", "classes"), deps])


def javap(cp, type_name):
    res = subprocess.run(["javap", "-public", "-cp", cp, type_name], capture_output=True, text=True)
    return res.stdout if res.returncode == 0 else ""


def tidy(text):
    lines = [l for l in text.splitlines() if not l.startswith("Compiled from")]
    # source-style names: java.lang implicit, nested types as Outer.Inner
    return "\n".join(lines).replace("java.lang.", "").replace("$", ".").strip()


def main():
    focal = sys.argv[1]
    cp = build_classpath()
    focal_api = javap(cp, focal)
    if not focal_api:
        sys.exit("javap could not read " + focal + "; compile the module first")

    seen, order = {focal}, []
    queue = sorted(set(PROJECT_TYPE.findall(focal_api)) - seen)
    while queue:
        name = queue.pop(0)
        if name in seen:
            continue
        seen.add(name)
        api = javap(cp, name)
        if not api:
            continue
        order.append(api)
        # follow nested types of collaborators (builders etc.), not the whole graph
        queue += sorted(t for t in set(PROJECT_TYPE.findall(api)) if "$" in t and t not in seen)

    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "api")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, focal.rsplit(".", 1)[1] + ".txt")
    with open(out, "w", encoding="utf8", newline="\n") as f:
        f.write("\n\n".join(tidy(a) for a in order) + "\n")
    print("wrote", out, "with", len(order), "types")


if __name__ == "__main__":
    main()
