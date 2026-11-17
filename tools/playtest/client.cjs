// Stationary synthetic observer; server teleports it between rounds.
// Disable client-side physics across void/spectator world changes; Citizens still simulates every NPC.
const mineflayer = require(process.env.RC1_CLIENT_MODULE || 'mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25641, username: 'Rc1Tester', auth: 'offline', version: '1.21.4', physicsEnabled: false });
bot.once('spawn', () => console.log('RC1_CLIENT_READY'));
bot.on('messagestr', message => console.log('CHAT ' + message));
bot.on('windowOpen', window => setTimeout(() => console.log('WINDOW ' + JSON.stringify({title:window.title, items:window.containerItems().map(i=>({name:i.name,slot:i.slot}))})),250));
bot.on('error', error => console.log('CLIENT_ERROR ' + error.message));
bot.on('kicked', reason => console.log('CLIENT_KICKED ' + JSON.stringify(reason)));
process.on('SIGTERM', () => { bot.quit(); setTimeout(()=>process.exit(),500); });
