#!/usr/bin/env python3
"""Render /etc/nginx/used.conf from gateway/nginx.conf.

`gateway/nginx.conf` stays the single source of truth for browser routes. This
script only translates the Docker-era addressing into the EC2 reality:

  * the Docker DNS resolver is dropped (there is no 127.0.0.11 on the host),
  * `*-service:808x` upstreams become loopback ports,
  * `listen 8080` becomes the host listener(s).

HTTPS is opt-in through /etc/used/https.env. The file is world-readable because
it holds no secrets, and it exists so that re-running install-host.sh keeps TLS
instead of silently downgrading the site back to plain HTTP.

The 443 listener and the HTTP->HTTPS redirect are emitted only when the
certificate files are actually present. That ordering matters: the certificate
must be issued while port 80 still serves the site, otherwise a failed issuance
would leave the domain pointing at a listener that has no certificate.

Usage:
    render-nginx.py TEMPLATE OUTPUT [--state /etc/used/https.env]
"""

import argparse
import pathlib
import re
import sys

ACME_ROOT = '/var/www/certbot'
CERT_ROOT = pathlib.Path('/etc/letsencrypt/live')

DOMAIN_RE = re.compile(r'^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$')

# Served from every listener: a proxy that forces HTTPS would otherwise turn the
# ACME challenge into a redirect and break renewal.
ACME_LOCATION = f'''
    # Let's Encrypt HTTP-01 challenge, served from a webroot so renewal never
    # depends on the application ports being up.
    location ^~ /.well-known/acme-challenge/ {{
        root {ACME_ROOT};
        default_type text/plain;
        try_files $uri =404;
    }}
'''


def read_state(path):
    state = {}
    candidate = pathlib.Path(path)
    if not candidate.is_file():
        return state
    for line in candidate.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        state[key.strip()] = value.strip()
    return state


def certificate_paths(domain):
    base = CERT_ROOT / domain
    return base / 'fullchain.pem', base / 'privkey.pem'


def translate(template_text):
    text = re.sub(r'^resolver .*;\n', '', template_text, flags=re.M)
    text = re.sub(r'http://[a-z]+-service:(808[0-8])', r'http://127.0.0.1:\1', text)
    marker = '\nserver {'
    index = text.find(marker)
    if index < 0:
        sys.exit('Template error: gateway/nginx.conf has no server block')
    return text[:index], text[index + 1:]


def insert_acme(block):
    anchor = '    location = /healthz {'
    if anchor not in block:
        sys.exit('Template error: cannot place the ACME location')
    return block.replace(anchor, ACME_LOCATION.lstrip('\n') + '\n' + anchor, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('template')
    parser.add_argument('output')
    parser.add_argument('--state', default='/etc/used/https.env')
    args = parser.parse_args()

    prefix, block = translate(pathlib.Path(args.template).read_text())
    if 'listen 8080;' not in block:
        sys.exit('Template error: expected "listen 8080;" in the server block')

    state = read_state(args.state)
    domain = state.get('HTTPS_DOMAIN', '')
    if domain and not DOMAIN_RE.match(domain):
        sys.exit(f'Invalid HTTPS_DOMAIN in {args.state}: {domain!r}')
    fullchain, privkey = certificate_paths(domain) if domain else (None, None)
    tls = bool(domain) and fullchain.is_file() and privkey.is_file()

    http_block = insert_acme(block.replace('listen 8080;', 'listen 80 default_server;', 1))

    blocks = [http_block]

    if tls:
        # The HTTP listener keeps answering 127.0.0.1 and the bare address, so
        # check-health.sh still exercises the routes over loopback. Only requests
        # carrying the real hostname are redirected.
        blocks.append(f'''server {{
    listen 80;
    server_name {domain};
    server_tokens off;
{ACME_LOCATION}
    location / {{ return 301 https://$host$request_uri; }}
}}''')
        tls_block = block.replace('listen 8080;', f'''listen 443 ssl default_server;
    http2 on;
    ssl_certificate     {fullchain};
    ssl_certificate_key {privkey};
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_session_cache   shared:used_tls:10m;
    ssl_session_timeout 1h;
    add_header Strict-Transport-Security "max-age=31536000" always;''', 1)
        tls_block = tls_block.replace('server_name _;', f'server_name {domain};', 1)
        blocks.append(insert_acme(tls_block))

    config = ('events {}\nhttp {\ninclude /etc/nginx/mime.types;\n'
              + prefix + '\n' + '\n\n'.join(blocks) + '\n}\n')
    pathlib.Path(args.output).write_text(config)
    print(f'Rendered {args.output} (https: {"on for " + domain if tls else "off"})')


if __name__ == '__main__':
    main()
