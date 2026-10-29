#!/usr/bin/env python3
"""Authenticated closed-loop HTTP measurement through the same-origin frontend proxy."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
from datetime import datetime, timedelta, timezone
import hashlib
import http.client
from http.cookies import SimpleCookie
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import threading
import time
import urllib.parse
import uuid
from zoneinfo import ZoneInfo


class Client:
    def __init__(self, base):
        url = urllib.parse.urlsplit(base)
        if url.scheme != 'http' or url.hostname not in ('localhost', '127.0.0.1') or url.path not in ('', '/'):
            raise ValueError('Use the HTTP loopback URL of a disposable frontend proxy.')
        self.connection = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=10)
        self.cookies = SimpleCookie()
        self.csrf = None

    def call(self, method, path, body=None, form=False):
        headers = {'Cookie': '; '.join(f'{key}={value.value}' for key, value in self.cookies.items())}
        if body is not None:
            body = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        if method == 'POST' and self.csrf:
            headers[self.csrf['headerName']] = self.csrf['token']
        self.connection.request(method, path, body=body, headers=headers)
        response = self.connection.getresponse()
        for name, value in response.getheaders():
            if name.lower() == 'set-cookie':
                self.cookies.load(value)
        content = response.read()
        return response.status, json.loads(content) if content else None

    def login(self, name, password):
        status, self.csrf = self.call('GET', '/api/v1/auth/csrf')
        assert status == 200
        status, _ = self.call('POST', '/api/v1/auth/login', {'username': name, 'password': password}, form=True)
        assert status == 204, 'Login failed'
        status, self.csrf = self.call('GET', '/api/v1/auth/csrf')
        assert status == 200

    def close(self):
        self.connection.close()


def summarize(rows):
    values = sorted(row['milliseconds'] for row in rows)
    return {'requests': len(rows), 'errors': sum(bool(row['error']) for row in rows),
            'min_ms': round(values[0], 3), 'mean_ms': round(sum(values) / len(values), 3),
            'p50_ms': round(values[math.ceil(.5 * len(values)) - 1], 3),
            'p95_ms': round(values[math.ceil(.95 * len(values)) - 1], 3),
            'max_ms': round(values[-1], 3)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--clients', type=int, default=10)
    parser.add_argument('--requests', type=int, default=1000, help='Measured requests per route')
    parser.add_argument('--warmup', type=int, default=200, help='Unmeasured requests per route')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.clients != 10 or args.requests < 1000 or args.warmup < 10:
        parser.error('This scenario requires 10 clients, >=1000 measured and >=10 warmup requests per route.')
    args.output.mkdir(parents=True, exist_ok=True)
    started = datetime.now(timezone.utc).isoformat()
    today = datetime.now(ZoneInfo('America/Sao_Paulo')).date()
    due = (today + timedelta(days=90)).isoformat()
    assignor = str(uuid.UUID(hashlib.md5(b'statement-benchmark-assignor-1').hexdigest()))
    selection = [
        ('all', {'from': '2026-01-01T00:00:00Z', 'to': '2026-07-01T00:00:00Z'}, 5000),
        ('assignor', {'from': '2026-01-01T00:00:00Z', 'to': '2026-07-01T00:00:00Z', 'assignorId': assignor}, 500),
        ('currency', {'from': '2026-01-01T00:00:00Z', 'to': '2026-07-01T00:00:00Z', 'currency': 'USD'}, 2500),
        ('combined', {'from': '2026-01-01T00:00:00Z', 'to': '2026-01-02T00:00:00Z', 'assignorId': assignor, 'currency': 'USD'}, 28),
    ]
    preflight = Client(args.base_url)
    try:
        preflight.login(os.environ.get('APP_USERNAME', 'operator'), os.environ['APP_PASSWORD'])
        status, inventory = preflight.call('GET', '/api/v1/receivables?page=0&size=1')
        assert status == 200 and inventory['total'] == 10000, 'Expected exactly 10000 receivables in the disposable fixture'
    finally:
        preflight.close()
    rows = []
    def phase(route, count, warmup):
        barrier = threading.Barrier(args.clients, timeout=60)
        def worker(client_id):
            client = Client(args.base_url)
            local_rows = []
            try:
                client.login(os.environ.get('APP_USERNAME', 'operator'), os.environ['APP_PASSWORD'])
                barrier.wait()
                for index in range(client_id, count, args.clients):
                    if route == 'simulation':
                        group = 'BRL' if index % 2 == 0 else 'USD'
                        method, path = 'POST', '/api/v1/pricing/simulations'
                        body = {'faceValue': '100000.00', 'dueDate': due, 'type': 'DUPLICATA', 'paymentCurrency': group}
                        expected_total = None
                    else:
                        group, filters, expected_total = selection[index % len(selection)]
                        method, body = 'GET', None
                        path = '/api/v1/settlements?' + urllib.parse.urlencode(dict(filters, page=0, size=20))
                    began = time.perf_counter_ns()
                    status, error = 0, ''
                    try:
                        status, result = client.call(method, path, body)
                        if status != 200:
                            error = 'HTTP_' + str(status)
                        elif route == 'simulation':
                            expected = '92859.94' if group == 'BRL' else '17094.67'
                            if result.get('paymentAmount') != expected or result.get('discountBrl') != '7140.06':
                                error = 'FINANCIAL_VALUE_MISMATCH'
                        elif result.get('total') != expected_total or len(result.get('items', [])) != 20:
                            error = 'STATEMENT_COUNT_MISMATCH'
                    except (OSError, http.client.HTTPException, ValueError):
                        error = 'TRANSPORT_OR_JSON_ERROR'
                        client.close()
                    elapsed = (time.perf_counter_ns() - began) / 1_000_000
                    local_rows.append({'route': route, 'group': group, 'client': client_id, 'sequence': index,
                                       'status': status, 'milliseconds': elapsed, 'error': error})
            finally:
                client.close()
            return local_rows
        began = time.perf_counter()
        with ThreadPoolExecutor(max_workers=args.clients) as executor:
            batches = list(executor.map(worker, range(args.clients)))
        batch = [row for group in batches for row in group]
        if warmup:
            assert not any(row['error'] for row in batch), f'Warmup failed for {route}: ' + str({row['error'] for row in batch if row['error']})
        else:
            rows.extend(batch)
        elapsed = time.perf_counter() - began
        print(('warmup' if warmup else 'measured'), route, json.dumps(summarize(batch)), 'elapsed_seconds', round(elapsed, 3), flush=True)
        return elapsed

    durations = {}
    for route in ['simulation', 'statement']:
        phase(route, args.warmup, True)
        durations[route] = phase(route, args.requests, False)
    with (args.output / 'http-latencies.csv').open('w', newline='') as output:
        writer = csv.DictWriter(output, fieldnames=['route', 'group', 'client', 'sequence', 'status', 'milliseconds', 'error'])
        writer.writeheader()
        for row in rows:
            writer.writerow(dict(row, milliseconds=f"{row['milliseconds']:.6f}"))
    report = {'started_at': started, 'finished_at': datetime.now(timezone.utc).isoformat(),
              'base_url': args.base_url, 'python': platform.python_version(), 'platform': platform.platform(),
              'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
              'clients': args.clients, 'warmup_per_route': args.warmup, 'measured_per_route': args.requests,
              'method': 'closed-loop; one persistent connection per client; independent session/CSRF; no automatic retries; includes response JSON validation; nearest-rank percentiles',
              'fixture': {'receivables': 10000, 'settlements': 5000, 'page': 0, 'size': 20},
              'routes': {route: dict(summarize([row for row in rows if row['route'] == route]),
                                     phase_seconds=round(durations[route], 3), target_p95_ms=300 if route == 'simulation' else 500)
                         for route in ['simulation', 'statement']},
              'groups': {route: {group: summarize([row for row in rows if row['route'] == route and row['group'] == group])
                                  for group in sorted({row['group'] for row in rows if row['route'] == route})}
                         for route in ['simulation', 'statement']}}
    (args.output / 'http-summary.json').write_text(json.dumps(report, indent=2) + '\n')
    assert all(result['errors'] == 0 for result in report['routes'].values()), 'Measured request errors; inspect the report'
    assert all(result['p95_ms'] <= result['target_p95_ms'] for result in report['routes'].values()), 'p95 exceeded the target; retain and explain the result'
    print('PASS local HTTP targets (not a production guarantee)', flush=True)


if __name__ == '__main__':
    main()
