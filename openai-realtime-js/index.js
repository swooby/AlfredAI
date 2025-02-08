
//
// Business logic
//

const TAG = "App";
const debug = true;
const debugRealtimeApi = true;

let textDangerousApiKey = null;
let textDangerousApiKeyDefault = null;

let radioWebrtc;
let radioWebsocket;
let buttonConnectDisconnect;
let audioControl;
let textareaConversation;
let inputText;
let buttonSendText;
let buttonInterrupt;
let buttonPushToTalk;

// Only needed/used by WebRTC
let microphone;

// Only needed/used by WebSockets
let wavStreamPlayer;
let wavRecorder;
let inputAudioBuffer;

// Either 'WEBRTC' or 'WEBSOCKET'
let connectionType;

let client;

let currentAssistentConversation;

function log(...args) {
    if (debug) {
        const date = new Date().toISOString();
        const logs = [`[${TAG}/${date}]`].concat(args).map((arg) => {
            if (typeof arg === 'object' && arg !== null) {
                return JSON.stringify(arg, null, 2);
            } else {
                return arg;
            }
        });
        console.log(...logs);
    }
    return true;
}

async function init() {
    textDangerousApiKey = document.getElementById('textDangerousApiKey');
    textDangerousApiKeyDefault = textDangerousApiKey.value;

    radioWebrtc = document.getElementById('radioWebrtc');
    radioWebsocket = document.getElementById('radioWebsocket');
    buttonConnectDisconnect = document.getElementById('buttonConnectDisconnect');
    audioControl = document.getElementById('audioControl');
    textareaConversation = document.getElementById('textareaConversation');
    inputText = document.getElementById('inputText');
    buttonSendText = document.getElementById('buttonSendText');
    buttonInterrupt = document.getElementById('buttonInterrupt');
    buttonPushToTalk = document.getElementById('buttonPushToTalk');

    updateControls();
}

//
// UI Event Handlers : BEGIN
//

function onTextDangerousApiKeyChange() {
    if (textDangerousApiKey.value === '') {
        textDangerousApiKey.value = textDangerousApiKeyDefault;
        textDangerousApiKey.type = 'text';
    }
    if (textDangerousApiKey.value !== textDangerousApiKeyDefault) {
        textDangerousApiKey.type = 'password';
    }
}

function connectDisconnect() {
    log('connectDisconnect()');
    if (client) {
        disconnect();
    } else {
        connect();
    }
}

function sendText(text) {
    text = text || inputText.value;
    log(`sendText("${text}")`);
    client.sendUserMessageContent([{
        type: 'input_text',
        text: text,
    }]);
}

function sendInterrupt() {
    if (!currentAssistentConversation) {
        log('sendInterrupt(): No current assistent conversation item to interrupt; ignoring');
        return;
    }
    const item = currentAssistentConversation.item;
    const elapsedMillis = Date.now() - currentAssistentConversation.startTime;
    const sampleCount = Math.floor(elapsedMillis / 1000.0 * client.conversation.defaultFrequency);
    log(`sendInterrupt(); cancelResponse(itemId=${item.id}, sampleCount=${sampleCount}`);
    client.cancelResponse(item.id, sampleCount);
}

async function pushToTalk(enable) {
    log(`pushToTalk(enable=${enable})`);
    if (enable) {
        await audioPlayerStop();
        sendInterrupt();
        client?.realtime.send('input_audio_buffer.clear');
        await audioRecorderStart();
    } else {
        await audioRecorderStop();
        client?.realtime.send('input_audio_buffer.commit');
        client.createResponse();
    }
}

function onConversationEvent(eventName, {item, delta}) {
    switch (eventName) {
        case 'conversation.updated':
            if (item.role === 'assistant' &&
                item.id !== currentAssistentConversation?.item.id) {
                currentAssistentConversation = {
                    item,
                    startTime: Date.now(),
                }
                log('conversation.updated: Set currentAssistentConversation=', currentAssistentConversation);
            }
            break;
        case 'conversation.item.completed':
            if (item.role == 'assistant' &&
                item.id === currentAssistentConversation?.item.id) {
                currentAssistentConversation = null;
                log('conversation.item.completed: Set currentAssistentConversation=null');
            }
            break;
    }
}

//
// UI Event Handlers : END
//

function updateControls() {
    if (client) {
        textDangerousApiKey.disabled = true;
        radioWebrtc.disabled = true;
        radioWebsocket.disabled = true;

        if (textareaConversation !== null) {
            textareaConversation.disabled = false;
        }
        inputText.disabled = false;
        buttonSendText.disabled = false;
        buttonInterrupt.disabled = false;
        buttonPushToTalk.disabled = false;

        buttonConnectDisconnect.innerText = 'Disconnect';
    } else {
        textDangerousApiKey.disabled = false;
        radioWebrtc.disabled = false;
        radioWebsocket.disabled = false;

        if (textareaConversation !== null) {
            textareaConversation.disabled = true;
        }
        inputText.disabled = true;
        buttonSendText.disabled = true;
        buttonInterrupt.disabled = true;
        buttonPushToTalk.disabled = true;

        buttonConnectDisconnect.innerText = 'Connect';
    }
}

async function disconnect() {
    if (wavStreamPlayer) {
        await wavStreamPlayer.interrupt();
        wavStreamPlayer = null;
    }
    if (wavRecorder) {
        await wavRecorder.end();
        wavRecorder = null;
    }
    inputAudioBuffer = null;

    client?.disconnect();
    client = null;

    updateControls();
}

async function connect() {
    await disconnect();

    let setAudioOutputCallback;
    let getMicrophoneCallback;

    console.info('For this app you may reasonably ignore the proceeding `Warning: Connecting using API key in the browser, this is not recommended`');
    const dangerousApiKey = textDangerousApiKey.value;

    // Is there a better/easier way to get the selected/checked item of a radio button group?
    connectionType = document.querySelector('input[name="connectionType"]:checked').value.toUpperCase();

    client = new RealtimeClient({
        transportType: connectionType,
        apiKey: dangerousApiKey,
        dangerouslyAllowAPIKeyInBrowser: true,
        debug: debugRealtimeApi,
    });
    client.on('close', (data) => {
        log('close', data);
        disconnect();
    });

    switch (connectionType) {
        case RealtimeTransportType.WEBRTC:
            setAudioOutputCallback = (audioSource) => {
                audioControl.srcObject = audioSource;
            };
            getMicrophoneCallback = async () => {
                const ms = await navigator.mediaDevices.getUserMedia({ audio: true });
                microphone = ms.getAudioTracks()[0];
                microphone.enabled = false;
                return microphone;
            };
            break;
        case RealtimeTransportType.WEBSOCKET:
            wavStreamPlayer = new WavStreamPlayer({ sampleRate: 24000 });
            await wavStreamPlayer.connect();
            client.on('conversation.updated', ({ item, delta }) => {
                if (delta?.audio) {
                    wavStreamPlayer.add16BitPCM(delta.audio, item.id);
                }
            });
            inputAudioBuffer = new Int16Array(0);
            wavRecorder = new WavRecorder({ sampleRate: 24000 });
            await wavRecorder.begin();
            // Will start in pushToTalk(true) and stop in pushToTalk(false)
            break;
        default:
            throw new Error(`Unknown connection type: "${connectionType}"`);
    }

    /*
    if (textareaConversation !== null) {
        client.on('server.response.audio_transcript.delta', (event) => {
            //log('server.response.audio_transcript.delta', event);
            const responseId = event.response_id;
            const delta = event.delta; // DO **NOT** TRIM!
            textareaConversation.value += delta;
        });
    }
    */

    client.on('conversation.updated', ({ item , delta}) => {
        onConversationEvent('conversation.updated', {item, delta});
    });
    client.on('conversation.item.completed', ({ item }) => {
        onConversationEvent('conversation.item.completed', {item});
    });

    updateControls();

    const sessionConfig = {
        model: 'gpt-4o-mini-realtime-preview',
        voice: 'ash',
        turn_detection: null,
    };

    await client.connect({ sessionConfig, setAudioOutputCallback, getMicrophoneCallback });
    await client.updateSession(sessionConfig);
}

/**
 * NOTE: There is no `audioPlayerStart()`
 * Per
 * https://github.com/keithwhor/wavtools/blob/main/README.md#wavstreamplayer-quickstart
 * "To restart, need to call .add16BitPCM() again"
 */
async function audioPlayerStop() {
    log('audioPlayerStop()');
    const trackOffset = await wavStreamPlayer?.interrupt();
    log('audioPlayerStop(): trackOffset=', trackOffset);
}

/**
 * Originally copied from:
 * https://github.com/openai/openai-realtime-console/blob/websockets/src/pages/ConsolePage.tsx#L226-L241
 * In push-to-talk mode, start recording
 * .appendInputAudio() for each sample
 */
async function audioRecorderStart() {
    log('audioRecorderStart()');
    switch (connectionType) {
        case RealtimeTransportType.WEBRTC:
            microphone.enabled = true;
            break;
        case RealtimeTransportType.WEBSOCKET:
            await wavRecorder.clear();
            await wavRecorder.record((data) => client.appendInputAudio(data.mono));
            break;
    }
}

/**
 * Originally copied from:
 * https://github.com/openai/openai-realtime-console/blob/websockets/src/pages/ConsolePage.tsx#L243-L252
 * In push-to-talk mode, stop recording
 */
async function audioRecorderStop() {
    log('audioRecorderStop()');
    switch (connectionType) {
        case RealtimeTransportType.WEBRTC:
            microphone.enabled = false;
            break;
        case RealtimeTransportType.WEBSOCKET:
            await wavRecorder.pause();
            break;
    }
}

