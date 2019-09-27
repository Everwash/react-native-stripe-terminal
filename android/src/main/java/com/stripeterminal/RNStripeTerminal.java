package com.stripeterminal;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Arguments;

import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.LogLevel;
import com.stripe.stripeterminal.TerminalException;
import com.stripe.stripeterminal.TerminalException;
import com.stripe.stripeterminal.DeviceType;
import com.stripe.stripeterminal.DiscoveryConfiguration;
import com.stripe.stripeterminal.Reader;
import com.stripe.stripeterminal.Callback;
import com.stripe.stripeterminal.ReaderCallback;
import com.stripe.stripeterminal.PaymentMethodCallback;
import com.stripe.stripeterminal.ReadReusableCardParameters;
import com.stripe.stripeterminal.PaymentMethod;
import com.stripe.stripeterminal.Terminal;
import com.stripe.stripeterminal.ReaderDisplayListener;
import com.stripe.stripeterminal.ReaderDisplayMessage;
import com.stripe.stripeterminal.ReaderInputOptions;
import com.stripe.stripeterminal.Cancelable;
import com.stripe.stripeterminal.ConnectionTokenProvider;

import com.stripeterminal.TokenProvider;
import com.stripeterminal.TerminalEventListener;

import android.util.Log;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;

public class RNStripeTerminal extends ReactContextBaseJavaModule {

    public static String clientSecret = "";
    Cancelable pendingDiscoverReaders = null;
    Cancelable pendingReadReusableCard;
    List<Reader> discoveredReadersList = null;

    public RNStripeTerminal(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    ReactContext getContext(){
        return getReactApplicationContext();
    }

    @Override
    public String getName() {
        return "RNStripeTerminal";
    }

    public void sendEventWithName(String eventName, WritableMap eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    public void sendEventWithName(String eventName, Object eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    public void sendEventWithName(String eventName, WritableArray eventData){
        getContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    public WritableMap errorMessage(String msg) {
        WritableMap errorParams = Arguments.createMap();
        errorParams.putString("error", msg);

        return errorParams;
    }

    @ReactMethod
    public void initializeTerminal() {
        TerminalEventListener listener = new TerminalEventListener() {};
        TokenProvider tokenProvider = new TokenProvider();

        try {
            Terminal.initTerminal(getContext().getApplicationContext(), LogLevel.VERBOSE, tokenProvider, listener);
        } catch (TerminalException e) {
            sendEventWithName("didInitializeTerminal", errorMessage("Failed to initialize Stripe Terminal."));

            e.printStackTrace();
        }

        abortDiscoverReaders();
        abortReadReusableCard();

        sendEventWithName("didInitializeTerminal", Arguments.createMap());

        Terminal.getInstance();
    }

    @ReactMethod
    public void setConnectionToken(String token, String errorMsg) {
        clientSecret = token;
        if (errorMsg != null) {
            sendEventWithName("setConnectionToken", errorMessage(errorMsg));
        } else {
            sendEventWithName("setConnectionToken", Arguments.createMap());
        }
    }

    public String fetchConnectionToken() {
        return clientSecret;
    }

    @ReactMethod
    public void discoverReaders() {
        abortDiscoverReaders();

        DiscoveryConfiguration config = new DiscoveryConfiguration(0, DeviceType.CHIPPER_2X, false);

        pendingDiscoverReaders = Terminal.getInstance().discoverReaders(config, readers -> {
            discoveredReadersList = readers;

            WritableArray readerArray = Arguments.createArray();
            for (Reader item : readers) {
                readerArray.pushMap(serializeReader(item));
            }

            sendEventWithName("readersDiscovered", readerArray);
        }, new Callback() {
            @Override
            public void onSuccess() {
                sendEventWithName("readersDiscoveryCompletion", Arguments.createMap());
            }

            @Override
            public void onFailure(TerminalException e) {
                sendEventWithName("readersDiscoveryCompletion", errorMessage(e.getErrorMessage()));
            }
        });
    }

    @ReactMethod
    public void connectReader(String serialNumber) {
        Reader selectedReader = null;
        if (discoveredReadersList != null && discoveredReadersList.size() > 0) {
            for (Reader reader:discoveredReadersList) {
                if (reader != null) {
                    if (reader.getSerialNumber().equals(serialNumber)) {

                        selectedReader = reader;
                    }
                }
            }
        }

        if (selectedReader != null) {
            Terminal.getInstance().connectReader(selectedReader, new ReaderCallback() {
                @Override
                public void onSuccess(Reader discoveredReader) {
                    sendEventWithName("readerConnection", Arguments.createMap());
                }

                @Override
                public void onFailure(TerminalException e) {
                    sendEventWithName("readerConnection", errorMessage("Failed to connect to reader."));
                }
            });
        } else {

            sendEventWithName("readerConnection", errorMessage("No reader found with provided serial number."));
        }
    }

    @ReactMethod
    public void readReusableCard() {
        abortReadReusableCard();

        ReadReusableCardParameters params = new ReadReusableCardParameters.Builder().build();

        pendingReadReusableCard = Terminal.getInstance().readReusableCard(params,
            new DisplayMessageListener(),
            new PaymentMethodCallback() {

            @Override
            public void onSuccess(PaymentMethod paymentMethod) {
                pendingReadReusableCard = null;

                WritableMap readReusableCardParams = Arguments.createMap();
                readReusableCardParams.putMap("paymentMethod", serializePaymentMethod(paymentMethod));

                sendEventWithName("readReusableCard", readReusableCardParams);
            }

            @Override
            public void onFailure(TerminalException e) {
                pendingReadReusableCard = null;
                sendEventWithName("readReusableCard", errorMessage(e.getErrorMessage())); 
            }
        });
    }

    public WritableMap serializePaymentMethod(PaymentMethod paymentMethod) {
        WritableMap paymentMethodMap = Arguments.createMap();

        paymentMethodMap.putString("stripeId", paymentMethod.getId());
        paymentMethodMap.putString("brand", paymentMethod.getCardDetails().getBrand());
        paymentMethodMap.putString("last4", paymentMethod.getCardDetails().getLast4());
        paymentMethodMap.putString("fingerprint", paymentMethod.getCardDetails().getFingerprint());

        return paymentMethodMap;
    }

    public WritableMap serializeReader(Reader reader) {
        WritableMap readerMap = Arguments.createMap();

        readerMap.putString("serialNumber", reader.getSerialNumber());
        readerMap.putString("deviceType", reader.getDeviceType().toString());

        return readerMap;
    }

    public void abortDiscoverReaders() {
        if ( pendingDiscoverReaders != null ) {
            pendingDiscoverReaders.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    sendEventWithName("abortDiscoverReadersCompletion", Arguments.createMap());
                }

                @Override
                public void onFailure(TerminalException e) {
                    sendEventWithName("abortDiscoverReadersCompletion", errorMessage(e.getErrorMessage()));
                }
            });
        }
    }

    @ReactMethod
    public void abortReadReusableCard() {
        if ( pendingReadReusableCard != null ) {
            pendingReadReusableCard.cancel(new Callback() {
                @Override
                public void onSuccess() {
                    sendEventWithName("abortReadReusableCardCompletion", Arguments.createMap());
                }

                @Override
                public void onFailure(TerminalException e) {
                    sendEventWithName("abortReadReusableCardCompletion", errorMessage(e.getErrorMessage()));
                }
            });
        }
    }

    public class DisplayMessageListener implements ReaderDisplayListener {
        @Override
        public void onRequestReaderDisplayMessage(ReaderDisplayMessage message) {
            WritableMap params = Arguments.createMap();
            params.putString("text", message.toString());

            sendEventWithName("didRequestReaderDisplayMessage", params);
        }

        @Override
        public void onRequestReaderInput(ReaderInputOptions options) {
            WritableMap params = Arguments.createMap();
            params.putString("text", options.toString());

            sendEventWithName("didRequestReaderInput", params);
        }
    }

}