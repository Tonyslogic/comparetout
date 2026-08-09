"""Derive table<->class dependencies for docs/model.

Two hops:
  1. DAO method -> tables      (from @Query SQL / @Insert-@Update-@Delete entity types)
  2. caller class -> DAO       (from field/param types + repository delegation)

Deliberately conservative: reports what it can prove from the source text and
lists what it could not resolve, rather than guessing.
"""
import re, os, json, collections

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, os.pardir, "app", "src", "main", "java",
                    "com", "tfcode", "comparetout")

TABLES = set("""DayRates PricePlans alphaESSRawEnergy alphaESSRawPower alphaESSTransformMeta
alphaESSTransformedData batteries costings discharge2grid evcharge evdivert heatpumps hwdivert
hwschedule hwsystem inverters loadprofile loadprofiledata loadshift paneldata panels
plan_combinations scenario2battery scenario2discharge scenario2evcharge scenario2evdivert
scenario2heatpump scenario2hwdivert scenario2hwschedule scenario2hwsystem scenario2inverter
scenario2loadprofile scenario2loadshift scenario2panel scenario_readiness scenarios
scenariosimulationdata""".split())
LOWER = {t.lower(): t for t in TABLES}

# entity class -> table, for @Insert/@Update/@Delete which name types not tables
ENTITY2TABLE = {}
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dirpath, fn)
        src = open(p, encoding="utf-8", errors="replace").read()
        m = re.search(r'@Entity\s*\(([^)]*)\)', src, re.S)
        if m and 'tableName' in m.group(1):
            t = re.search(r'tableName\s*=\s*"([^"]+)"', m.group(1))
            if t:
                ENTITY2TABLE[fn[:-5]] = t.group(1)

def tables_in_sql(sql):
    found = set()
    for kw in re.finditer(r'\b(?:FROM|JOIN|INTO|UPDATE|DELETE\s+FROM)\s+([A-Za-z_0-9]+)',
                          sql, re.I):
        name = kw.group(1).lower()
        if name in LOWER:
            found.add(LOWER[name])
    return found

def dao_methods(path):
    """-> {methodName: set(tables)}"""
    src = open(path, encoding="utf-8", errors="replace").read().replace("\r", "")
    out = {}
    # split on annotation boundaries; each chunk = annotations + signature
    for m in re.finditer(
            r'((?:@(?:Query|Insert|Update|Delete|Transaction|RawQuery)[^\n]*\n?)+)'
            r'((?:\s*(?:public|protected|abstract|static|final|synchronized)\s+)*'
            r'[\w<>,\[\]\. ]+?\s+(\w+)\s*\()', src):
        annos, _, name = m.group(1), m.group(2), m.group(3)
        tabs = set()
        for q in re.finditer(r'@Query\s*\(\s*"((?:[^"\\]|\\.)*)"', annos, re.S):
            tabs |= tables_in_sql(q.group(1))
        # multi-line concatenated query strings
        blk = src[m.start():m.start() + 2000]
        qm = re.search(r'@Query\s*\((.*?)\)\s*\n', blk, re.S)
        if qm:
            tabs |= tables_in_sql(qm.group(1))
        if re.search(r'@(?:Insert|Update|Delete)', annos):
            sig = src[m.start(2):m.start(2) + 400]
            for ent, tbl in ENTITY2TABLE.items():
                if re.search(r'\b' + ent + r'\b', sig):
                    tabs.add(tbl)
        out.setdefault(name, set())
        out[name] |= tabs
    return out

DAO_FILES = [os.path.join(ROOT, "model", f) for f in
             ("ScenarioDAO.java", "PricePlanDAO.java", "CostingDAO.java", "AlphaEssDAO.java")]
DAO_FILES += [os.path.join(ROOT, "model", "dao", f)
              for f in sorted(os.listdir(os.path.join(ROOT, "model", "dao")))
              if f.endswith(".java")]

dao_table = {}      # dao -> set(tables)
method_table = {}   # methodName -> set(tables)  (union across DAOs)
dao_method_count = {}
for p in DAO_FILES:
    dao = os.path.basename(p)[:-5]
    ms = dao_methods(p)
    dao_method_count[dao] = len(ms)
    dao_table[dao] = set()
    for name, tabs in ms.items():
        dao_table[dao] |= tabs
        method_table.setdefault(name, set())
        method_table[name] |= tabs

# ---- repository: which DAO methods does each repo method reach? ----
repo_src = open(os.path.join(ROOT, "model", "ToutcRepository.java"),
                encoding="utf-8", errors="replace").read().replace("\r", "")
repo_methods = {}
for m in re.finditer(r'\n\s*public\s+[\w<>,\[\]\. ]+?\s+(\w+)\s*\([^)]*\)\s*\{', repo_src):
    name = m.group(1)
    depth, i = 0, m.end() - 1
    while i < len(repo_src):
        if repo_src[i] == '{':
            depth += 1
        elif repo_src[i] == '}':
            depth -= 1
            if depth == 0:
                break
        i += 1
    body = repo_src[m.end():i]
    tabs = set()
    for call in re.finditer(r'\.(\w+)\s*\(', body):
        tabs |= method_table.get(call.group(1), set())
    repo_methods[name] = tabs

# ---- callers ----
caller_tables = collections.defaultdict(set)
caller_repo_calls = collections.defaultdict(set)
unresolved = collections.defaultdict(set)
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not (fn.endswith(".java") or fn.endswith(".kt")):
            continue
        p = os.path.join(dirpath, fn)
        rel = os.path.relpath(p, ROOT).replace("\\", "/")
        if rel.startswith("model/") and ("DAO" in fn or fn == "ToutcRepository.java"):
            continue
        src = open(p, encoding="utf-8", errors="replace").read()
        if "ToutcRepository" not in src and "Repository" not in src:
            continue
        for call in re.finditer(
                r'(?:mToutcRepository|mRepository|repository|repo|mRepo|toutcRepository)\s*\.\s*(\w+)\s*\(',
                src):
            name = call.group(1)
            caller_repo_calls[rel].add(name)
            if name in repo_methods and repo_methods[name]:
                caller_tables[rel] |= repo_methods[name]
            elif name not in repo_methods:
                unresolved[rel].add(name)

out = {
    "tables": sorted(TABLES),
    "entity2table": ENTITY2TABLE,
    "dao_method_count": dao_method_count,
    "dao_tables": {k: sorted(v) for k, v in sorted(dao_table.items())},
    "repo_method_tables": {k: sorted(v) for k, v in sorted(repo_methods.items()) if v},
    "repo_methods_no_tables": sorted(k for k, v in repo_methods.items() if not v),
    "caller_tables": {k: sorted(v) for k, v in sorted(caller_tables.items()) if v},
    "caller_repo_calls": {k: sorted(v) for k, v in sorted(caller_repo_calls.items())},
    "unresolved_calls": {k: sorted(v) for k, v in sorted(unresolved.items()) if v},
}
dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "depmap.json")
json.dump(out, open(dst, "w", encoding="utf-8"), indent=1)

print("tables:", len(TABLES), "entities:", len(ENTITY2TABLE))
print("dao methods:", sum(dao_method_count.values()))
print("repo methods parsed:", len(repo_methods),
      "with tables:", sum(1 for v in repo_methods.values() if v))
print("caller files with resolved tables:", len(out["caller_tables"]))
print("\nDAO -> tables")
for k, v in out["dao_tables"].items():
    print(f"  {k} ({dao_method_count[k]}): {len(v)} tables")
print("\ntable -> caller count")
rev = collections.defaultdict(set)
for c, ts in out["caller_tables"].items():
    for t in ts:
        rev[t].add(c)
for t in sorted(TABLES):
    print(f"  {t}: {len(rev.get(t, ()))}")
