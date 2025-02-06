
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

// Only needed/used by WebRTC?
let microphone;

// Only needed/used by WebSockets?
let wavStreamPlayer;
let wavRecorder;
let inputAudioBuffer;

let connectionType;

let realtime;

let currentConversation;

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
    if (realtime) {
        disconnect();
    } else {
        connect();
    }
}

function sendText(text) {
    text = text || inputText.value;
    log(`sendText("${text}")`);
    sendUserMessageContent([{
        type: 'input_text',
        text: text,
    }]);
}

function sendInterrupt() {
    if (!currentConversation) {
        log('sendInterrupt(): No conversation item to interrupt; ignoring');
        return;
    }
    log('sendInterrupt()');
    // NOTE: `client.js` `cancelResponse` sends `response.cancel` [with no `response_id`] **BEFORE** `conversation.item.truncate`
    sendResponseCancel();//currentConversation.responseId);
    const elapsedMillis = Date.now() - currentConversation.startTime;
    realtime?.send('conversation.item.truncate', {
        item_id: currentConversation.itemId,
        content_index: 0,
        audio_end_ms: elapsedMillis,
    });
}

async function pushToTalk(enable) {
    log(`pushToTalk(enable=${enable})`);
    if (enable) {
        await audioPlayerStop();
        sendInterrupt();
        realtime?.send('input_audio_buffer.clear');
        await audioRecorderStart();
    } else {
        await audioRecorderStop();
        realtime?.send('input_audio_buffer.commit');
        sendResponseCreate();
    }
}

//
// UI Event Handlers : END
//

function updateControls() {
    if (realtime) {
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

    realtime?.disconnect();
    realtime = null;

    updateControls();
}

async function connect() {
    await disconnect();

    let getMicrophoneCallback;
    let setAudioOutputCallback;

    console.info('For this app you may reasonably ignore the proceeding `Warning: Connecting using API key in the browser, this is not recommended`');
    const dangerousApiKey = textDangerousApiKey.value;

    // Is there a better/easier way to get the selected/checked item of a radio button group?
    connectionType = document.querySelector('input[name="connectionType"]:checked').value.toUpperCase();

    realtime = new RealtimeAPI({
        transportType: connectionType,
        apiKey: dangerousApiKey,
        dangerouslyAllowAPIKeyInBrowser: true,
        debug: debugRealtimeApi,
    });

    switch (connectionType) {
        case RealtimeTransportType.WEBRTC:
            getMicrophoneCallback = async () => {
                const ms = await navigator.mediaDevices.getUserMedia({ audio: true });
                microphone = ms.getAudioTracks()[0];
                microphone.enabled = false;
                return microphone;
            };
            setAudioOutputCallback = (audioSource) => {
                audioControl.srcObject = audioSource;
            };

            realtime.on('server.output_audio_buffer.stopped', (event) => {
                log('server.output_audio_buffer.stopped', event);
                const responseId = event.response_id;
                if (responseId === currentConversation?.responseId) {
                    currentConversation = null;
                    log('server.output_audio_buffer.audio_stopped: Set currentConversation=', currentConversation);
                }
            });
        
            break;
        case RealtimeTransportType.WEBSOCKET:
            wavStreamPlayer = new WavStreamPlayer({ sampleRate: 24000 });
            await wavStreamPlayer.connect();
            realtime.on('server.response.audio.delta', (event) => {
                //log('server.response.audio.delta', event);
                //const responseId = event.response_id;
                const itemId = event.item_id;
                const delta = event.delta;
                const arrayBuffer = RealtimeUtils.base64ToArrayBuffer(delta);
                wavStreamPlayer.add16BitPCM(arrayBuffer, itemId);
            });

            inputAudioBuffer = new Int16Array(0);
            wavRecorder = new WavRecorder({ sampleRate: 24000 });
            await wavRecorder.begin();
            // Will start in pushToTalk(true) and stop in pushToTalk(false)

            realtime.on('server.response.content_part.done', (event) => {
                //log('response.content_part.done', event);
                const responseId = event.response_id;
                if (responseId === currentConversation?.responseId) {
                    currentConversation = null;
                    log('server.response.content_part.done: Set currentConversation=', currentConversation);
                }
            });
    
            break;
        default:
            throw new Error(`Unknown connection type: "${connectionType}"`);
    }

    if (textareaConversation !== null) {
        realtime.on('server.response.audio_transcript.delta', (event) => {
            //log('server.response.audio_transcript.delta', event);
            const responseId = event.response_id;
            const delta = event.delta; // DO **NOT** TRIM!
            textareaConversation.value += delta;
        });
    }
    realtime.on('server.response.content_part.added', (event) => {
        //log('server.response.content_part.added', event);
        const responseId = event.response_id;
        if (responseId !== currentConversation?.responseId) {
            currentConversation = {
                itemId: event.item_id,
                responseId: responseId,
                startTime: Date.now(),
            };
            log('server.response.content_part.added: Set currentConversation=', currentConversation);
        }
    });

    updateControls();

    const sessionConfig = {
        model: 'gpt-4o-mini-realtime-preview',
        voice: 'ash',
        turn_detection: null,
    };

    await realtime.connect({ sessionConfig, getMicrophoneCallback, setAudioOutputCallback });

    await realtime.send('session.update', {
        session: sessionConfig
    });
}

/**
 * Variation of `client.js`'s `cancelResponse(id, sampleCount = 0)`
 */
function sendResponseCancel(responseId) {
    log(`sendResponseCancel(${responseId})`);
    /**
     * Interestingly, in `client.js`'s `cancelResponse(id, sampleCount = 0)`:
     * 1. It only ever sends an empty `'response.cancel'; it **NEVER** adds a `response_id` to the payload!
     * 2. `id` is actually the **item_id**, and it used to send `conversation.item.truncate`.
     * 3. Says that only role=assistant type=message items with audio content can be truncated.
     * 4. Sends a [empty] `response.cancel` message and **BEFORE** checking for audio content.
     * 5. Find and sends the `content_index`, even though the documentation says "Set this to 0.".
     *  https://platform.openai.com/docs/api-reference/realtime-client-events/conversation/item/truncate#realtime-client-events/conversation/item/truncate-content_index
     * 
     * Seems like a lot of unnecessary logic overloaded into their `cancelResponse` function. :/
     * I think it is fine to just do any needful extras outside of this function,
     * and leave this function to just do what is says it does: send `response.cancel`...
     * with the optional documented `response_id`.
     */
    realtime?.send('response.cancel', {
        response_id: responseId,
    });
}

/**
 * Variation of `client.js`'s `createResponse()`
 */
function sendResponseCreate() {
    log('sendResponseCreate()');
    realtime?.send('response.create');
}

/**
 * Originally copied from `client.js`
 * Sends user message content and generates a response
 * @param {Array<InputTextContentType|InputAudioContentType>} content
 * @returns {true}
 */
function sendUserMessageContent(content = []) {
    if (content.length) {
        for (const c of content) {
            if (c.type === 'input_audio') {
                if (c.audio instanceof ArrayBuffer || c.audio instanceof Int16Array) {
                    c.audio = RealtimeUtils.arrayBufferToBase64(c.audio);
                }
            }
        }
        realtime?.send('conversation.item.create', {
            item: {
                type: 'message',
                role: 'user',
                content,
            },
        });
    }
    sendResponseCreate();
    return true;
}

/**
 * Originally copied from `client.js`
 * Only used by WebSocket.
 * Appends user audio to the existing audio buffer
 * @param {Int16Array|ArrayBuffer} arrayBuffer
 * @returns {true}
 */
function appendInputAudio(arrayBuffer) {
    if (arrayBuffer.byteLength > 0) {
        realtime?.send('input_audio_buffer.append', {
            audio: RealtimeUtils.arrayBufferToBase64(arrayBuffer),
        });
        inputAudioBuffer = RealtimeUtils.mergeInt16Arrays(
            inputAudioBuffer,
            arrayBuffer,
        );
    }
    return true;
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
            await wavRecorder.record((data) => appendInputAudio(data.mono));
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

