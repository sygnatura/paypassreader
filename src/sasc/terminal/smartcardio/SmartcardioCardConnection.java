/*
 * Copyright 2010 sasc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sasc.terminal.smartcardio;

import java.io.IOException;

import sasc.terminal.CardConnection;
import sasc.terminal.CardResponse;
import sasc.terminal.Terminal;
import sasc.terminal.TerminalException;
import sasc.util.Log;
import sasc.util.Util;

/**
 * Any handling of procedure bytes and GET REPONSE/GET DATA in javax.smartcardio
 * should be disabled, because of improper handling of the CLS byte in some
 * cases.
 *
 * Thus, the "procedure byte handling" of the the TAL (Terminal Abstraction
 * Layer), is moved to a higher layer.
 *
 * @author sasc
 */
public class SmartcardioCardConnection implements CardConnection {


    @Override
    public CardResponse transmit(byte[] cmd) throws TerminalException {
        if (cmd == null) {
            throw new IllegalArgumentException("Argument 'cmd' cannot be null");
        }

        if (cmd.length < 4) {
            throw new IllegalArgumentException("APDU must be at least 4 bytes long: " + cmd.length);
        }

        Log.debug("cmd bytes: " + Util.prettyPrintHexNoWrap(cmd));

        /*
         * case 1 : |CLA|INS|P1 |P2 |                    len = 4 
         * case 2s: |CLA|INS|P1 |P2 |LE |                len = 5 
         * case 3s: |CLA|INS|P1 |P2 |LC |...BODY...|     len = 6..260 
         * case 4s: |CLA|INS|P1 |P2 |LC |...BODY...|LE | len = 7..261
         *
         * (Extended length is not currently supported) 
         * case 2e: |CLA|INS|P1 |P2|00 |LE1|LE2|                    len = 7 
         * case 3e: |CLA|INS|P1 |P2 |00|LC1|LC2|...BODY...|         len = 8..65542 
         * case 4e: |CLA|INS|P1 |P2 |00|LC1|LC2|...BODY...|LE1|LE2| len =10..65544
         *
         * EMV uses case 2, case 3 or case 4 
         * Procedure byte 61 is case 2
         * Procedure byte 6c is case 2
         *
         * EMV: When required in a command message, Le shall always be set to
         * '00' Note that for SmartcardIO: CommandAPDU(byte[]) transforms Le=0
         * into 256 CommandAPDU(int, int.. etc) uses Ne instead of Le. So to
         * send Le=0x00 to ICC, then send Ne=256
         *
         * Use CommandAPDU(byte[]) for case 1 & 3 (those without Le) Use
         * CommandAPDU(int, int, int, int, int) for case 2 (but transform
         * Le=0x00 into Ne=256. SmartcardIO changes this back into Le=0x00) Use
         * CommandAPDU(int, int, int, int, byte, int, int, int) for case 4 (but
         * transform Le=0x00 into Ne=256. SmartcardIO changes this back into
         * Le=0x00)
         */
        CardResponseImpl response = null;
        try {
        	short sw;
            byte recv[] = pl.paypassreader.MainActivity.tagcomm.transceive(cmd);

            byte[] data = recv;
            int n = (data != null) ? data.length : 0;
            sw = (n > 1) ? (short) ((data[n - 2] << 8) | data[n - 1]) : 0;

            byte sw1 = (byte) recv[recv.length - 2];
            byte sw2 = (byte) recv[recv.length - 1];

            response = new CardResponseImpl(recv, sw1, sw2, sw);
        } catch (IOException ce) {
            throw new TerminalException("Error occured while transmitting command: " + Util.byteArrayToHexString(cmd), ce);
        }
        return response;
    }

    @Override
    public byte[] getATR() {
        return new byte[0];
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public Terminal getTerminal() {
        throw new UnsupportedOperationException("Not implemented yet");
//        return new SmartCardIOTerminal(smartCardIOTerminal);
    }

    @Override
    public String getConnectionInfo() {
        return "";
    }

    @Override
    public String getProtocol() {
        return "";
    }

    @Override
    public boolean disconnect(boolean attemptReset) throws TerminalException {
       /* try {
            card.disconnect(!attemptReset);
            return true;
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }*/
    	return true;
    }

    /**
     * Perform warm reset
     *
     */
    @Override
    public void resetCard() throws TerminalException {
/*        try {
            //From scuba:
            // WARNING: Woj: the meaning of the reset flag is actually
            // reversed w.r.t. to the official documentation, false means
            // that the card is going to be reset, true means do not reset
            // This is a bug in the smartcardio implementation from SUN
            // Moreover, Linux PCSC implementation goes numb if you try to
            // disconnect a card that is not there anymore.

            // From sun/security/smartcardio/CardImpl.java:
            // SCardDisconnect(cardId, (reset ? SCARD_LEAVE_CARD : SCARD_RESET_CARD));
            // (http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/6-b14/sun/security/smartcardio/CardImpl.java?av=f)
            // The BUG: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7047033
            if (smartCardIOTerminal.isCardPresent()) {
                card.disconnect(false);
            }
            card = smartCardIOTerminal.connect("*");
            channel = card.getBasicChannel();
        } catch (CardException ex) {
            throw new TerminalException(ex);
        }*/
    }

    @Override
    public byte[] transmitControlCommand(int code, byte[] data) throws TerminalException {
/*        try {
            byte[] response = card.transmitControlCommand(code, data);
            return response;
        } catch (CardException ex) {
            Throwable cause = ex.getCause();
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            throw new TerminalException(cause.getMessage());
        }*/
    	return new byte[0];
    }

    private class CardResponseImpl implements CardResponse {

        private byte[] data;
        private byte sw1;
        private byte sw2;
        private short sw;

        CardResponseImpl(byte[] data, byte sw1, byte sw2, short sw) {
            this.data = data;
            this.sw1 = sw1;
            this.sw2 = sw2;
            this.sw = sw;
        }

        @Override
        public byte[] getData() {
            return data;
        }

        @Override
        public byte getSW1() {
            return sw1;
        }

        @Override
        public byte getSW2() {
            return sw2;
        }

        @Override
        public short getSW() {
            return sw;
        }
    }
}
