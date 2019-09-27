import { NativeModules, NativeEventEmitter } from 'react-native';
import createHooks from './hooks';

const { RNStripeTerminal } = NativeModules;

class StripeTerminal {

    // Fetch connection token. Overwritten in call to initialize
    _fetchConnectionToken = () =>
        Promise.reject('You must initialize RNStripeTerminal first.');

    constructor() {
        this.listener = new NativeEventEmitter(RNStripeTerminal);
    
        this.listener.addListener('setConnectionToken', () => {
            RNStripeTerminal.initializeTerminal();
        });

        this.listener.addListener('didInitializeTerminal', () => {
            this.discoverReaders();
        });
    
        this._createListeners([
            'log',
            'readersDiscovered',
            'readerSoftwareUpdateProgress',
            'connectionTokenCompletion',
            'didRequestReaderInput',
            'didRequestReaderDisplayMessage',
            'didReportReaderEvent',
            'didReportLowBatteryWarning',
            'didChangePaymentStatus',
            'didChangeConnectionStatus',
            'didReportUnexpectedReaderDisconnect',
            'didInitializeTerminal'
        ]);
    }
    
    _createListeners(keys) {
        keys.forEach(k => {
            this[`add${k[0].toUpperCase() + k.substring(1)}Listener`] = listener =>
            this.listener.addListener(k, listener);
            this[`remove${k[0].toUpperCase() + k.substring(1)}Listener`] = listener =>
            this.listener.removeListener(k, listener);
        });
    }
    
    _wrapPromiseReturn(event, call, key) {
        return new Promise((resolve, reject) => {
            const subscription = this.listener.addListener(event, data => {
            if (data && data.error) {
                reject(data);
            } else {
                resolve(key ? data[key] : data);
            }
            subscription.remove();
            });

            call();
        });
    }

    initialize({ fetchConnectionToken }) {
        this._fetchConnectionToken = fetchConnectionToken;
        // Fetch connection token from Stripe API endpoint
        // Set it in the RNStripeTerminal class to be consumed by TokenProvider interface
        // Which allows the initialization of the Terminal
        fetchConnectionToken().then((token) => {
            if (token) {
                RNStripeTerminal.setConnectionToken(token, null);
            } else {
                throw new Error('User-supplied `fetchConnectionToken` resolved successfully, but no token was returned.');
            }
          }).catch(err => RNStripeTerminal.setConnectionToken(null, err.message || 'Error in user-supplied `fetchConnectionToken`.'))
    }

    discoverReaders() {
        return this._wrapPromiseReturn('readerDiscoveryCompletion', () => {
            RNStripeTerminal.discoverReaders();
        });
    }

    connectReader(serialNumber) {
        return this._wrapPromiseReturn('readerConnection', () => {
            RNStripeTerminal.connectReader(serialNumber);
        });
    }

    readReusableCard() {
        return this._wrapPromiseReturn('readReusableCard', () => {
            RNStripeTerminal.readReusableCard();
        });
    }

    abortReadReusableCard() {
        return this._wrapPromiseReturn('abortReadReusableCardCompletion', () => {
            RNStripeTerminal.abortReadReusableCard();
        });
    }

}
const StripeTerminal_ = new StripeTerminal();
export default StripeTerminal_;

export const {
    useStripeTerminalState,
    useStripeTerminalCreatePayment,
    useStripeTerminalConnectionManager,
  } = createHooks(StripeTerminal_);