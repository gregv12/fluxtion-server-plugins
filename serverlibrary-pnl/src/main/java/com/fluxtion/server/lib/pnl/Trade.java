/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.lib.pnl;

import com.fluxtion.server.lib.pnl.refdata.Instrument;
import com.fluxtion.server.lib.pnl.refdata.Symbol;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Trade {
    private static long globalSequenceNumber;
    private transient Symbol symbol;
    private String symbolName;
    private double dealtVolume;
    private double contraVolume;
    private double fee;
    private Instrument feeInstrument;
    private long id;

    public Trade(Symbol symbol, double dealtVolume, double contraVolume, double fee) {
        this.symbol = symbol;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        globalSequenceNumber++;
        this.id = globalSequenceNumber;
    }

    public Trade(Symbol symbol, double dealtVolume, double contraVolume, double fee, long id) {
        this.symbol = symbol;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.id = id;
        if (id > globalSequenceNumber) {
            globalSequenceNumber = id;
        } else {
            globalSequenceNumber++;
            this.id = globalSequenceNumber;
        }
    }

    public Trade(String symbolName, double dealtVolume, double contraVolume, double fee) {
        this.symbolName = symbolName;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        globalSequenceNumber++;
        this.id = globalSequenceNumber;
    }

    public Trade(String symbolName, double dealtVolume, double contraVolume, double fee,  long id) {
        this.symbolName = symbolName;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.id = id;
        if (id > globalSequenceNumber) {
            globalSequenceNumber = id;
        } else {
            globalSequenceNumber++;
            this.id = globalSequenceNumber;
        }
    }

    public Trade(String symbolName, double dealtVolume, double contraVolume, double fee, Instrument feeInstrument) {
        this.symbolName = symbolName;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.feeInstrument = feeInstrument;
        globalSequenceNumber++;
        this.id = globalSequenceNumber;
    }

    public Trade(String symbolName, double dealtVolume, double contraVolume, double fee, Instrument feeInstrument, long id) {
        this.symbolName = symbolName;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.feeInstrument = feeInstrument;
        this.id = id;
        if (id > globalSequenceNumber) {
            globalSequenceNumber = id;
        } else {
            globalSequenceNumber++;
            this.id = globalSequenceNumber;
        }
    }

    public Trade(Symbol symbol, double dealtVolume, double contraVolume, double fee, Instrument feeInstrument) {
        this.symbol = symbol;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.feeInstrument = feeInstrument;
        globalSequenceNumber++;
        this.id = globalSequenceNumber;
    }

    public Trade(Symbol symbol, double dealtVolume, double contraVolume, double fee, Instrument feeInstrument, long id) {
        this.symbol = symbol;
        this.dealtVolume = dealtVolume;
        this.contraVolume = contraVolume;
        this.fee = fee;
        this.feeInstrument = feeInstrument;
        this.id = id;
        if (id > globalSequenceNumber) {
            globalSequenceNumber = id;
        } else {
            globalSequenceNumber++;
            this.id = globalSequenceNumber;
        }
    }

    public Instrument getDealtInstrument() {
        return symbol.dealtInstrument();
    }

    public Instrument getContraInstrument() {
        return symbol.contraInstrument();
    }

    public Instrument getFeeInstrument() {
        if(feeInstrument == null){
            feeInstrument = Instrument.INSTRUMENT_USD;
        }
        return feeInstrument;
    }
}
