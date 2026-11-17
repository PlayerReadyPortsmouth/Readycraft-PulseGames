#!/usr/bin/env python3
"""Run the explicitly installed RC1 probe on a disposable loopback server, through ready-gate.
Dependencies: Paper 1.21.4, compatible Citizens, built PulseGames and playtestJar,
and a separately installed mineflayer package. No production service is contacted.
"""
import argparse, json, os, pathlib, queue, re, shutil, subprocess, threading, time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server-dir', required=True, type=pathlib.Path)
parser.add_argument('--client-dir', required=True, type=pathlib.Path)
parser.add_argument('--games', help='Comma separated catalogue IDs; default: every game')
parser.add_argument('--seasonal-seconds', type=int, default=130)
parser.add_argument('--smoke-seconds', type=int, default=16)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[2]
server = args.server_dir.resolve()
assert (server/'RC1_LOCAL_ONLY').is_file(), 'Create an RC1_LOCAL_ONLY sentinel in the disposable server first'
assert 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
assert (server/'paper.jar').is_file() and (server/'plugins/Citizens.jar').is_file()
version = next(line.split('=',1)[1] for line in (root/'gradle.properties').read_text().splitlines() if line.startswith('pluginVersion='))
jar = root/f'build/libs/PulseGames-{version}.jar'
probe = root/f'build/playtest/RC1-LocalProbe-{version}.jar'
shutil.copy2(jar, server/'plugins/PulseGames.jar')
shutil.copy2(probe, server/'plugins/RC1-LocalProbe.jar')
config = server/'plugins/PulseGames'; config.mkdir(parents=True, exist_ok=True)
(config/'config.yml').write_text((root/'src/main/resources/config.yml').read_text().replace('bots:\n  enabled: false', 'bots:\n  enabled: true', 1))
seasons = (root/'src/main/resources/seasons.yml').read_text().replace('enabled: false', 'enabled: true', 1)
(config/'seasons.yml').write_text(seasons)
lines = []; client_lines = []; events = queue.Queue()
def pump(process, output, target):
    with output.open('w') as stream:
        for line in process.stdout:
            if 'set-cookie' in line.lower():
                line='[dependency response cookie header redacted]\n'
            stream.write(line); stream.flush(); target.append(line)
            if 'RC1 ' in line or 'RC1_CLIENT' in line or 'SEVERE' in line: print(line.strip(), flush=True)
            events.put(None)
def wait_for(needle, start=0, timeout=120, source=lines):
    end = time.monotonic()+timeout
    while time.monotonic()<end:
        found = next((line for line in source[start:] if needle in line), None)
        if found: return found
        if paper.poll() is not None: raise RuntimeError('Paper exited; inspect server-console.log')
        try: events.get(timeout=.5)
        except queue.Empty: pass
    raise TimeoutError(needle)
def command(value):
    paper.stdin.write(value+'\n'); paper.stdin.flush()
def pause(seconds):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        if paper.poll() is not None: raise RuntimeError('Paper exited')
        time.sleep(min(.5,max(0,end-time.monotonic())))
paper = subprocess.Popen(['java','-Xms512m','-Xmx2g','-jar','paper.jar','nogui'],cwd=server,
                         stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
threading.Thread(target=pump,args=(paper,server/'server-console.log',lines),daemon=True).start()
client = None; results = []
try:
    wait_for('Done (',timeout=180)
    assert any('Enabling RC1LocalProbe' in line for line in lines), 'Probe did not load'
    assert not any('Queue bots unavailable' in line for line in lines), 'Citizens provider did not connect'
    env = dict(os.environ,RC1_CLIENT_MODULE=str(args.client_dir.resolve()/'node_modules/mineflayer'))
    client = subprocess.Popen(['node',str(root/'tools/playtest/client.cjs')],env=env,
                              stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    threading.Thread(target=pump,args=(client,server/'client-console.log',client_lines),daemon=True).start()
    wait_for('RC1_CLIENT_READY',timeout=45,source=client_lines)
    command('rc1 prepare'); wait_for('RC1 PREPARED',timeout=180)
    n=len(lines);command('rc1 catalogue')
    catalogue=wait_for('RC1 CATALOGUE',n).split('RC1 CATALOGUE ',1)[1].strip().split(',')
    selected=args.games.split(',') if args.games else ['presentpanic','grinchhunt']+[g for g in catalogue if g not in ('presentpanic','grinchhunt')]
    for game in selected:
        c=len(client_lines); command('rc1 guide '+game)
        window=json.loads(wait_for('WINDOW ',c,timeout=15,source=client_lines).split('WINDOW ',1)[1])
        assert any(i['slot']==11 and i['name']=='writable_book' for i in window['items']), 'Missing how-to-play card: '+game
        assert any(i['slot']==13 and i['name']=='gold_nugget' for i in window['items']), 'Missing rewards card: '+game
        n=len(lines); command('rc1 round '+game); queued=wait_for('RC1 QUEUED '+game,n)
        try:
            wait_for('RC1 STATE '+game+' RUNNING',n,timeout=30)
        except TimeoutError:
            results.append({'game':game,'running':False,'controller':'not-started','samples':[],
                            'errors':['No RUNNING transition; inspect admission/dependency messages'],
                            'messages':[l.strip() for l in client_lines[-25:]]})
            command('rc1 cleanup'); cleaned=wait_for('RC1 CLEANUP',n); pause(3)
            assert 'active=0;' in cleaned and 'instances=0' in cleaned, 'Failed admission leaked actors: '+game
            assert not any(p.is_dir() for p in server.glob('pulse_*')), 'Failed admission leaked a world: '+game
            (server/'results.json').write_text(json.dumps(results,indent=2)+'\n')
            continue
        freeze=re.search(r'freeze=(\d+)',queued)
        elapsed = 0; duration = args.seasonal_seconds if game in ('presentpanic','grinchhunt') else args.smoke_seconds+(int(freeze[1]) if freeze else 0)
        while elapsed<duration:
            pause(min(10,duration-elapsed)); elapsed+=min(10,duration-elapsed)
            command('rc1 sample'); pause(.3)
            if any('RC1 STATE '+game+' RESETTING' in line for line in lines[n:]): break
        command('rc1 timeout');pause(6);command('rc1 sample');pause(.3)
        observed=lines[n:]
        result={'game':game,'running':any('RC1 STATE '+game+' RUNNING' in l for l in observed),
                'controller':'supported' if any('controller=supported' in l for l in observed) else 'lifecycle-only',
                'samples':[l.strip() for l in observed if 'RC1 SAMPLE' in l],
                'errors':[l.strip() for l in observed if any(e in l for e in ('SEVERE','Exception','Bot controller failed','RC1 PROBE FAILED'))]}
        if game == 'presentpanic' and not any(re.search(r'delivered=\{[^}]', l) for l in result['samples']):
            result['errors'].append('Seasonal objective check failed: no bot delivery observed')
        if game == 'grinchhunt' and not any(re.search(r'banked=[1-9]|caught=\[[^]]', l) for l in result['samples']):
            result['errors'].append('Seasonal objective check failed: no cave banking or capture observed')
        results.append(result)
        command('rc1 cleanup');cleaned=wait_for('RC1 CLEANUP',n);pause(3)
        assert 'active=0;' in cleaned and 'instances=0' in cleaned, 'Contestants or instances leaked after '+game
        assert not any(p.is_dir() for p in server.glob('pulse_*')), 'A cloned match world remains after '+game
        (server/'results.json').write_text(json.dumps(results,indent=2)+'\n')
    print('RC1_FINISHED '+json.dumps({'games':len(results),'started':sum(r['running'] for r in results),'errors':sum(len(r['errors']) for r in results)}),flush=True)
    if any(not r['running'] or r['errors'] for r in results): raise RuntimeError('Review failed gameplay probes in results.json')
finally:
    if client is not None:
        client.terminate()
        try: client.wait(timeout=10)
        except subprocess.TimeoutExpired: client.kill()
    if paper.poll() is None:
        command('stop')
        try: paper.wait(timeout=45)
        except subprocess.TimeoutExpired: paper.terminate();paper.wait(timeout=10)
