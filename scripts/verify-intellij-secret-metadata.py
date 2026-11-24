#!/usr/bin/env python3
"""Compare IntelliJ YAML inspection with and without source-visible Secret metadata.

Uses an isolated synthetic project, placeholder values and separate IDE state. Never opens
application-local.yml from a checkout. Requires an installed IDEA with Spring support and
cached Spring dependencies (run scripts/verify.sh first).
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import tomllib
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
GOOGLE = "invoice-watcher.oauth.google"
SECRET_KEY = GOOGLE + ".client-secret"
CONTROL_KEY = GOOGLE + ".nonexistent"
ADDITIONAL = "META-INF/additional-spring-configuration-metadata.json"


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def library_roots(cache, java_home):
    catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
    boot_version = catalog["versions"]["spring-boot"]
    boms = list((cache / "org.springframework.boot/spring-boot-dependencies" / boot_version).glob("*/*.pom"))
    if len(boms) != 1:
        raise SystemExit("Expected the cached Spring Boot BOM; run scripts/verify.sh first")
    bom = ET.parse(boms[0]).getroot()
    namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
    spring_version = bom.findtext("p:properties/p:spring-framework.version", namespaces=namespace)
    validation_version = bom.findtext("p:properties/p:jakarta-validation.version", namespaces=namespace)
    if not spring_version or not validation_version:
        raise SystemExit("Spring Boot BOM lacks the fixture's Spring/validation versions")
    roots = [f'<root url="jrt://{escape(str(java_home))}!/java.base" />']
    modules = [
        ("org.springframework.boot", boot_version, ["spring-boot", "spring-boot-autoconfigure"]),
        ("org.springframework", spring_version, ["spring-core", "spring-context", "spring-beans",
                                                "spring-expression", "spring-aop"]),
        ("jakarta.validation", validation_version, ["jakarta.validation-api"]),
    ]
    for group, version, names in modules:
        for name in names:
            module = cache / group / name / version
            candidates = [p for p in module.glob("*/*.jar")
                if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
            if len(candidates) != 1:
                raise SystemExit(f"Expected one cached {group}:{name} JAR; run scripts/verify.sh first")
            roots.append(f'<root url="jar://{escape(str(candidates[0]))}!/" />')
    return "".join(roots)


def fixture(project, roots, metadata, linked):
    modules = {"secret": [], "google": ["secret"], "app": ["secret", "google"]}
    declarations = []
    for name, dependencies in modules.items():
        path = project / name
        source = '<sourceFolder url="file://$MODULE_DIR$/src/main/java" isTestSource="false" />'
        resource = '<sourceFolder url="file://$MODULE_DIR$/src/main/resources" type="java-resource" />'
        entries = "".join(f'<orderEntry type="module" module-name="{d}" />' for d in dependencies)
        library = f'<orderEntry type="module-library"><library><CLASSES>{roots}</CLASSES><JAVADOC/><SOURCES/></library></orderEntry>'
        facet = '' if name != "app" else ('<component name="FacetManager"><facet type="Spring" name="Spring">'
            '<configuration><fileset id="spring" name="Spring Application Context" removed="false">'
            '<file>file://$MODULE_DIR$/src/main/java/fixture/App.java</file>'
            '</fileset></configuration></facet></component>')
        write(path / f"{name}.iml", '<module type="JAVA_MODULE" version="4">'
            '<component name="NewModuleRootManager" inherit-compiler-output="true">'
            f'<content url="file://$MODULE_DIR$">{source}{resource}</content>'
            f'<orderEntry type="sourceFolder" forTests="false" />{entries}{library}</component>{facet}</module>')
        declarations.append(f'<module fileurl="file://$PROJECT_DIR$/{name}/{name}.iml" '
                            f'filepath="$PROJECT_DIR$/{name}/{name}.iml" />')
    write(project / ".idea/modules.xml", '<project version="4"><component name="ProjectModuleManager">'
          '<modules>' + "".join(declarations) + '</modules></component></project>')
    # java.base is a library root; no dependency on the user's registered SDK names or IDE settings.
    write(project / ".idea/misc.xml", '<project version="4"><component name="ProjectRootManager" '
          'version="2" languageLevel="JDK_25" /></project>')
    for module, source in [("secret", "libs/secret/api"), ("google", "libs/oauth/google/api")]:
        for relative in (ROOT / source / "src/main/java").rglob("*.java"):
            if relative.name not in {"Secret.java", "GoogleOauthProperties.java"}:
                continue
            target = project / module / "src/main/java" / relative.relative_to(ROOT / source / "src/main/java")
            write(target, relative.read_text())
    write(project / "app/src/main/java/fixture/App.java", '''package fixture;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.guslarz.invoicewatcher.oauth.google.api.GoogleOauthProperties;
@SpringBootApplication
@EnableConfigurationProperties(GoogleOauthProperties.class)
public class App {}
''')
    yaml = ('invoice-watcher:\n  oauth:\n    google:\n      client-id: fixture-client\n'
            '      client-secret: fixture-encrypted-placeholder\n      nonexistent: fixture-control\n')
    config = project / "app/src/main/resources" / ("application-local.yml" if linked else "application.yml")
    if linked:
        target = project.parent / (project.name + "-local.yml")
        write(target, yaml)
        config.parent.mkdir(parents=True, exist_ok=True)
        config.symlink_to(target)
    else:
        write(config, yaml)
    if metadata:
        declaration = ROOT / "libs/oauth/google/api/src/main/resources" / ADDITIONAL
        write(project / "google/src/main/resources" / ADDITIONAL, declaration.read_text())


def inspect(idea, properties, project, profile, results, log):
    env = dict(os.environ, IDEA_PROPERTIES=str(properties))
    with log.open("w") as output:
        result = subprocess.run([str(idea / "bin/inspect.sh"), str(project), str(profile),
            str(results), "-v2", "-d", str(project / "app/src/main/resources")],
            stdout=output, stderr=subprocess.STDOUT, env=env, timeout=300)
    if result.returncode:
        raise SystemExit(f"IntelliJ inspection failed ({result.returncode}); see {log}")
    report = results / "SpringBootApplicationYaml.xml"
    if not report.is_file():
        raise SystemExit(f"No YAML inspection report; baseline/control not established; see {log}")
    warnings = []
    for problem in ET.parse(report).getroot().iter("problem"):
        description = problem.findtext("description", "")
        match = re.fullmatch(r"Cannot resolve configuration property '([^']+)'", description)
        if not match:
            raise SystemExit(f"Unexpected YAML inspection result: {description}")
        warnings.append(match.group(1))
    return sorted(warnings)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--idea-home", required=True, type=Path)
    parser.add_argument("--java-home", type=Path, default=Path(os.environ.get("JAVA_HOME", Path(shutil.which("java")).resolve().parents[1])))
    parser.add_argument("--gradle-cache", type=Path, default=Path.home() / ".gradle/caches/modules-2/files-2.1")
    parser.add_argument("--output", type=Path, default=ROOT / "build/intellij-secret-metadata")
    args = parser.parse_args()
    args.idea_home = args.idea_home.resolve()
    if not (args.idea_home / "bin/inspect.sh").is_file():
        parser.error("--idea-home must contain bin/inspect.sh")
    output = args.output.resolve()
    # A fresh directory prevents stale project models and reports from affecting the comparison.
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="run-", dir=output))
    properties = run / "idea.properties"
    write(properties, f"idea.config.path={run}/config\nidea.system.path={run}/system\n"
          f"idea.log.path={run}/log\njava.awt.headless=true\nidea.initially.ask.config=false\n")
    profile = run / "profile.xml"
    write(profile, '<component name="InspectionProjectProfileManager"><profile version="1.0">'
          '<option name="myName" value="Secret metadata regression" />'
          '<inspection_tool class="SpringBootApplicationYaml" enabled="true" level="WARNING" '
          'enabled_by_default="true" /></profile></component>')
    roots = library_roots(args.gradle_cache, args.java_home.resolve())
    summary = {"idea": json.loads((args.idea_home / "product-info.json").read_text())["buildNumber"], "cases": {}}
    for name, metadata, linked in [("baseline", False, False), ("source-metadata", True, False),
                                    ("linked-yaml", True, True)]:
        project = run / "projects" / name
        fixture(project, roots, metadata, linked)
        warnings = inspect(args.idea_home, properties, project, profile, run / (name + "-results"), run / (name + ".log"))
        expected = sorted([SECRET_KEY, CONTROL_KEY] if not metadata else [CONTROL_KEY])
        if warnings != expected:
            raise SystemExit(f"{name}: expected {expected}, got {warnings}; inspect {run}")
        summary["cases"][name] = warnings
        print(f"PASS {name}: {warnings}", flush=True)
    write(run / "summary.json", json.dumps(summary, indent=2) + "\n")
    print(f"IntelliJ inspection evidence: {run}")


if __name__ == "__main__":
    main()
