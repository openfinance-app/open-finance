#!/usr/bin/env python3
"""Build and test an isolated encrypted JAR/UI with synthetic users and an owned SQLite DB."""

import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true', help='Use an already built JAR and UI')
    args, playwright_args = parser.parse_known_args()
    repo = Path(__file__).resolve().parents[1]
    ui = repo / 'openfinance-ui'
    artifacts = repo / 'target/browser-tests'
    artifacts.mkdir(parents=True, exist_ok=True)
    if not args.skip_build:
        subprocess.run(['npm', 'run', 'build'], cwd=ui, check=True)
        subprocess.run(['mvn', '-B', '-DskipTests', 'package'], cwd=repo, check=True)

    pom = ET.parse(repo / 'pom.xml').getroot()
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    name = pom.findtext('m:artifactId', namespaces=ns)
    version = pom.findtext('m:version', namespaces=ns)
    source_jar = repo / 'target' / f'{name}-{version}.jar'
    with tempfile.TemporaryDirectory(prefix='openfinance-browser-') as temporary:
        runtime = Path(temporary)
        jar = runtime / 'browser.jar'
        # Preserve nested library compression and launcher metadata while embedding the current UI.
        with zipfile.ZipFile(source_jar) as source, zipfile.ZipFile(jar, 'w') as packaged:
            for entry in source.infolist():
                if not entry.filename.startswith('BOOT-INF/classes/static/'):
                    packaged.writestr(entry, source.read(entry))
            for asset in (ui / 'dist').rglob('*'):
                if asset.is_file():
                    packaged.write(asset, 'BOOT-INF/classes/static/' + asset.relative_to(ui / 'dist').as_posix())
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        base_url = f'http://127.0.0.1:{port}'
        env = {key: value for key, value in os.environ.items()
               if not key.startswith(('SPRING_DATASOURCE_', 'SPRING_FLYWAY_', 'SPRING_JPA_'))}
        env.update(JWT_SECRET=secrets.token_urlsafe(48), APPLICATION_ENCRYPTION_ENABLED='true',
                   APPLICATION_LIVE_DEMO_ENABLED='false', APPLICATION_TEST_DATA_SEED_ENABLED='false',
                   APPLICATION_LOGO_FETCH_ENABLED='false', APPLICATION_SEED_ENABLED='false')
        command = ['java', '-jar', str(jar), '--spring.profiles.active=prod',
                   '--server.address=127.0.0.1', f'--server.port={port}',
                   f'--spring.datasource.url=jdbc:sqlite:{runtime / "browser.db"}?foreign_keys=on&journal_mode=WAL&busy_timeout=10000',
                   '--logging.level.root=WARN', '--logging.file.name=' + str(artifacts / 'backend.log')]
        with (artifacts / 'launcher.log').open('w') as log:
            process = subprocess.Popen(command, cwd=runtime, env=env, stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 120
                while time.monotonic() < deadline:
                    if process.poll() is not None:
                        raise RuntimeError('Packaged backend exited; inspect target/browser-tests logs')
                    try:
                        with urllib.request.urlopen(base_url + '/api/v1/health', timeout=2) as response:
                            if response.status == 200:
                                break
                    except (urllib.error.URLError, TimeoutError):
                        time.sleep(0.5)
                else:
                    raise TimeoutError('Packaged backend did not become ready')
                registration = urllib.request.Request(base_url + '/api/v1/auth/register',
                    data=json.dumps({'username': 'real_test_user', 'email': 'real_test_user@example.invalid',
                                     'password': 'Password123!', 'masterPassword': 'RealMaster123!'}).encode(),
                    headers={'Content-Type': 'application/json'})
                with urllib.request.urlopen(registration, timeout=30) as response:
                    if response.status != 201:
                        raise RuntimeError('Failed to provision the synthetic browser user')
                env.update(CI='true', PLAYWRIGHT_BASE_URL=base_url)
                result = subprocess.run(['npx', 'playwright', 'test', '--retries=0', *playwright_args], cwd=ui, env=env)
                return result.returncode
            finally:
                process.terminate()
                try:
                    process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


if __name__ == '__main__':
    raise SystemExit(main())
