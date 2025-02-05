import { RealtimeEventHandler } from './event_handler.js';
import { RealtimeUtils } from './utils.js';

export class RealtimeApiWebRTC extends RealtimeEventHandler {
    /**
     * Create a new RealtimeClientWebRTC instance
     * @param {{url?: string, apiKey?: string, dangerouslyAllowAPIKeyInBrowser?: boolean, debug?: boolean}} [settings]
     * @returns {RealtimeClientWebRTC}
     */
    constructor({ url, apiKey, dangerouslyAllowAPIKeyInBrowser, debug } = {}) {
        super();
        this.defaultUrl = 'https://api.openai.com/v1/realtime';
        this.url = url || this.defaultUrl;
        this.apiKey = apiKey || null;
        this.debug = !!debug;
        this.peerConnection = null;
        this.dataChannel = null;
        if (globalThis.document && this.apiKey) {
            if (!dangerouslyAllowAPIKeyInBrowser) {
                throw new Error(
                    `Can not provide API key in the browser without "dangerouslyAllowAPIKeyInBrowser" set to true`,
                );
            }
        }
    }

    /**
     * Tells us whether or not the WebRTC is connected
     * @returns {boolean}
     */
    isConnected() {
        return !!this.peerConnection;
    }

    /**
     * Writes WebRTC logs to console
     * @param  {...any} args
     * @returns {true}
     */
    log(...args) {
        if (this.debug) {
            const date = new Date().toISOString();
            const logs = [`[WebRTC/${date}]`].concat(args).map((arg) => {
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

    /**
     * Connects to Realtime API WebRTC Server
     * @param {{model?: string}} [settings]
     * @returns {Promise<true>}
     */
    async connect(sessionConfig = { model: 'gpt-4o-realtime-preview-2024-12-17', voice: 'verse' },
        getMicrophoneCallback,
        setAudioOutputCallback,
    ) {
        sessionConfig = {
            model: 'gpt-4o-realtime-preview-2024-12-17',
            voice: 'verse',
            ...sessionConfig,
        };
        log(`connect(sessionConfig=${sessionConfig}, ...)`);
        if (!this.apiKey && this.url === this.defaultUrl) {
            console.warn(`No apiKey provided for connection to "${this.url}"`);
        }
        if (this.isConnected()) {
            throw new Error(`Already connected`);
        }
        if (globalThis.document && this.apiKey) {
            console.warn(
                'Warning: Connecting using API key in the browser, this is not recommended',
            );
        }
        const emphemeralApiToken = await this._requestEphemeralApiToken(this.apiKey, sessionConfig);
        await this._init(emphemeralApiToken, sessionConfig.model, getMicrophoneCallback, setAudioOutputCallback);
    }

    /**
     * Initially from:
     * https://platform.openai.com/docs/guides/realtime-webrtc#creating-an-ephemeral-token
     */
    async _requestEphemeralApiToken(dangerousApiKey, sessionConfig) {
        const r = await fetch(`${this.url}/sessions`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${dangerousApiKey}`,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(sessionConfig),
        });
        const data = await r.json();
        return data.client_secret.value;
    }

    /**
     * Initially from:
     * https://platform.openai.com/docs/guides/realtime-webrtc#connection-details
     */
    async _init(ephemeralApiToken, model, getMicrophoneCallback, setAudioOutputCallback) {
        log(`init(...)`);
        this.peerConnection = new RTCPeerConnection();

        this.peerConnection.addTrack(await getMicrophoneCallback());
        this.peerConnection.ontrack = (e) => setAudioOutputCallback(e.streams[0]);

        return new Promise(async (resolve, reject) => {
            const dataChannel = this.peerConnection.createDataChannel('oai-events');
            dataChannel.addEventListener('open', () => {
                log('Data channel is open');
                this.dataChannel = dataChannel;
                resolve(true);
            });
            dataChannel.addEventListener('closing', () => {
                log('Data channel is closing');
            });
            dataChannel.addEventListener('close', () => {
                this.disconnect();
                log('Data channel is closed');
                this.dispatch('close', { error: false });
            });
            dataChannel.addEventListener('message', (e) => {
                const message = JSON.parse(e.data);
                this.receive(message.type, message);
            });

            // Start the session using the Session Description Protocol (SDP)
            const offer = await this.peerConnection.createOffer();
            await this.peerConnection.setLocalDescription(offer);
            const sdpResponse = await fetch(`${this.url}?model=${model}`, {
                method: 'POST',
                body: offer.sdp,
                headers: {
                    Authorization: `Bearer ${ephemeralApiToken}`,
                    'Content-Type': 'application/sdp'
                },
            });
            await this.peerConnection.setRemoteDescription({
                type: 'answer',
                sdp: await sdpResponse.text(),
            });
        });
    }

    /**
     * Disconnects from Realtime API server
     */
    disconnect() {
        log('disconnect()');
        if (this.dataChannel) {
            this.dataChannel.close();
            this.dataChannel = null;
        }
        if (this.peerConnection) {
            this.peerConnection.close();
            this.peerConnection = null;
        }
    }

    /**
     * Receives an event from WebRTC and dispatches as "server.{eventName}" and "server.*" events
     * @param {string} eventName
     * @param {{[key: string]: any}} event
     * @returns {true}
     */
    receive(eventName, event) {
        this.log(`received:`, eventName, event);
        this.dispatch(`server.${eventName}`, event);
        this.dispatch('server.*', event);
        return true;
    }

    /**
     * Sends an event to WebRTC and dispatches as "client.{eventName}" and "client.*" events
     * @param {string} eventName
     * @param {{[key: string]: any}} event
     * @returns {true}
     */
    send(eventName, data) {
        if (!this.isConnected()) {
            throw new Error(`RealtimeAPI is not connected`);
        }
        data = data || {};
        if (typeof data !== 'object') {
            throw new Error(`data must be an object`);
        }
        const event = {
            event_id: RealtimeUtils.generateId('evt_'),
            type: eventName,
            ...data,
        };
        this.dispatch(`client.${eventName}`, event);
        this.dispatch('client.*', event);
        this.log(`sent:`, eventName, event);
        this.dataChannel.send(JSON.stringify(event));
        return true;
    }
}
