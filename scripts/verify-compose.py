#!/usr/bin/env python3
"""Verify an isolated Compose deployment. Changes synthetic data and stops/restarts its services."""
import argparse
from datetime import datetime, timedelta, timezone
import http.cookiejar
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo


class Client:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None

    def request(self, method, path, body=None, expected=200, headers=None, form=False):
        values = {'X-Probe-Secret': 'task7-secret-canary'}
        values.update(headers or {})
        if body is not None:
            data = (urllib.parse.urlencode(body) if form else json.dumps(body)).encode()
            values['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
        else:
            data = None
        if method == 'POST' and self.csrf:
            values[self.csrf['headerName']] = self.csrf['token']
        request = urllib.request.Request(self.base + path, data=data, headers=values, method=method)
        try:
            response = self.opener.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            content = response.read()
            assert response.status in (expected if isinstance(expected, tuple) else (expected,)), f'{method} {path}: expected {expected}, got {response.status}'
            result = json.loads(content) if content else None
            return result, dict(response.headers), content

    def login(self, username, password):
        self.csrf = self.request('GET', '/api/v1/auth/csrf')[0]
        self.request('POST', '/api/v1/auth/login', {'username': username, 'password': password}, 204, form=True)
        self.csrf = self.request('GET', '/api/v1/auth/csrf')[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:4200')
    parser.add_argument('--project', required=True, help='Use only a disposable Compose project')
    parser.add_argument('--env-file', default='.env', help='Compose interpolation only; export login variables separately')
    args = parser.parse_args()
    compose = ['docker', 'compose', '-p', args.project, '--env-file', args.env_file]
    def lifecycle(*command):
        subprocess.run(compose + list(command), check=True, stdout=subprocess.DEVNULL)

    def wait_health(path, expected):
        deadline = time.monotonic() + 45
        while True:
            try:
                result = anonymous.request('GET', path, expected=expected)[0]
                assert result == {'status': 'UP' if expected == 200 else 'DOWN'}
                return
            except (AssertionError, urllib.error.URLError, TimeoutError):
                if time.monotonic() >= deadline:
                    raise
                time.sleep(.5)

    log_since = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
    anonymous = Client(args.base_url)
    operator = Client(args.base_url)
    admin = Client(args.base_url)
    anonymous.request('GET', '/api/v1/receivables', expected=401)
    wait_health('/actuator/health/readiness', 200)
    operator.login(os.environ.get('APP_USERNAME', 'operator'), os.environ['APP_PASSWORD'])
    admin.login(os.environ.get('ADMIN_USERNAME', 'admin'), os.environ['ADMIN_PASSWORD'])
    assert any(cookie.name == 'JSESSIONID' and cookie.has_nonstandard_attr('HttpOnly')
               and cookie.get_nonstandard_attr('SameSite') == 'Lax' for cookie in operator.cookies)
    _, headers, _ = operator.request('GET', '/api/v1/auth/me', headers={'X-Correlation-ID': 'task7-secret-canary'})
    uuid.UUID(headers['X-Correlation-ID'])
    correlation = str(uuid.uuid4())
    invalid, _, _ = operator.request('POST', '/api/v1/receivables', {'password': 'task7-secret-canary'}, 400,
                                     headers={'X-Correlation-ID': correlation})
    assert invalid['correlationId'] == correlation
    assignor = operator.request('POST', '/api/v1/assignors', {'name': 'Cedente sintético de verificação'}, 201)[0]
    admin.request('POST', '/api/v1/fx-rates/sync', {}, (200, 201))
    runtime_user = os.environ.get('DATABASE_USER', 'srm_app')
    runtime_env = dict(os.environ, PGPASSWORD=os.environ['DATABASE_PASSWORD'])
    privileges = subprocess.check_output(compose + ['exec', '-T', '-e', 'PGPASSWORD', 'postgres',
        'psql', '-X', '-U', runtime_user, '-d', 'srm', '-At', '-c',
        "SELECT current_user, has_table_privilege(current_user,'settlements','INSERT'), "
        "has_table_privilege(current_user,'settlements','UPDATE'), "
        "has_table_privilege(current_user,'settlements','DELETE'), "
        "has_table_privilege(current_user,'fx_rates','UPDATE'), "
        "has_table_privilege(current_user,'fx_rates','DELETE'), "
        "(SELECT count(*) > 1 FROM pg_stat_activity WHERE usename=current_user)"], env=runtime_env, text=True).strip()
    assert privileges == runtime_user + '|t|f|f|f|f|t', 'Runtime privileges or application connection do not match'
    print('PASS runtime SQL login: application connections, INSERT allowed and history UPDATE/DELETE denied', flush=True)
    print('PASS proxy, health, session cookies, login/CSRF, correlation and FX synchronization', flush=True)
    due = (datetime.now(ZoneInfo('America/Sao_Paulo')).date() + timedelta(days=90)).isoformat()
    def count(currency):
        path = '/actuator/metrics/settlements.confirmed?tag=' + urllib.parse.quote('currency:' + currency)
        return admin.request('GET', path)[0]['measurements'][0]['value']
    originals = {currency: count(currency) for currency in ['BRL', 'USD']}
    totals = {}
    try:
        lifecycle('stop', 'fx-mock')
        began = time.monotonic()
        admin.request('POST', '/api/v1/fx-rates/sync', {}, 503)
        assert time.monotonic() - began < 3.0, 'Provider failure exceeded the 3-second bound'
        for currency in ['BRL', 'USD']:
            data = {'faceValue': '100000.00', 'dueDate': due, 'type': 'DUPLICATA', 'paymentCurrency': currency}
            price = operator.request('POST', '/api/v1/pricing/simulations', data)[0]
            assert price['presentValueBrl'] == '92859.94' and price['discountBrl'] == '7140.06'
            assert price['paymentAmount'] == ('92859.94' if currency == 'BRL' else '17094.67')
            receivable = operator.request('POST', '/api/v1/receivables', dict(data, assignorId=assignor['id']), 201)[0]
            intent = {'receivableId': receivable['id'], 'currency': currency, 'expectedPaymentAmount': price['paymentAmount'],
                      'expectedFxRateId': price['fxRate']['id'] if price['fxRate'] else None}
            key = {'Idempotency-Key': str(uuid.uuid4())}
            settlement, first_headers, first_body = operator.request('POST', '/api/v1/settlements', intent, 201, key)
            for _ in range(3):
                _, retry_headers, retry_body = operator.request('POST', '/api/v1/settlements', intent, 201, key)
                assert retry_headers['Location'] == first_headers['Location'] and retry_body == first_body
            assert count(currency) == originals[currency] + 1
            totals[currency] = settlement['paymentAmount']
        now = datetime.now(timezone.utc)
        query = urllib.parse.urlencode({'from': (now - timedelta(hours=1)).isoformat(), 'to': (now + timedelta(minutes=1)).isoformat(),
                                        'assignorId': assignor['id'], 'page': 0, 'size': 20})
        statement = operator.request('GET', '/api/v1/settlements?' + query)[0]
        assert statement['total'] == 2
        assert {item['currency']: item['paymentTotal'] for item in statement['totalsByCurrency']} == totals
        assert len(admin.request('GET', '/actuator/metrics/pricing.duration')[0]['measurements']) >= 2
        print('PASS provider stopped: C1/C3 simulation, settlement, three exact replays, counters and separate totals')
    finally:
        lifecycle('start', 'fx-mock')
    try:
        lifecycle('stop', 'postgres')
        wait_health('/actuator/health/readiness', 503)
        wait_health('/actuator/health/liveness', 200)
        print('PASS database stopped: readiness 503 with no details, liveness 200')
    finally:
        lifecycle('start', 'postgres')
    wait_health('/actuator/health/readiness', 200)
    log_output = subprocess.check_output(compose + ['logs', '--no-color', '--no-log-prefix', '--since', log_since, 'backend'], text=True)
    for forbidden in ['task7-secret-canary'] + [os.environ[name] for name in ['APP_PASSWORD', 'ADMIN_PASSWORD', 'DATABASE_PASSWORD', 'MIGRATION_PASSWORD'] if os.environ.get(name)]:
        assert forbidden not in log_output, 'Secret found in logs'
    events = [json.loads(line) for line in log_output.splitlines() if line.startswith('{')]
    app_events = [event for event in events if event.get('event') in ['request_failed', 'http_request', 'settlement_confirmed', 'fx_sync_attempt']]
    assert app_events and all(event['@timestamp'].endswith('Z') and event.get('outcome') for event in app_events)
    confirms = [event for event in app_events if event.get('event') == 'settlement_confirmed']
    assert len(confirms) == 2
    for event in confirms:
        for name in ['actorId', 'receivableId', 'settlementId', 'correlationId']:
            assert event[name]
    assert any(event.get('correlationId') == correlation and event.get('code') == 'INVALID_INPUT' for event in app_events)
    print('PASS JSON events: UTC, correlation, outcome, audit identifiers and no probe secrets; exactly two confirmation events')
    print('PASS all Compose operational checks; synthetic records remain in the disposable project')


if __name__ == '__main__':
    main()
