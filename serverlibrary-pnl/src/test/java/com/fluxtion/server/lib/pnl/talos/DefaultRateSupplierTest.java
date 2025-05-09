package com.fluxtion.server.lib.pnl.talos;

import com.fluxtion.server.lib.pnl.MidPrice;
import com.fluxtion.server.lib.pnl.refdata.DefaultRateMapSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class DefaultRateSupplierTest {

    @Test
    public void testMidPriceMapping(){
        DefaultRateMapSupplier rateSupplier = new DefaultRateMapSupplier() {
            @Override
            public Map<String, Double> getSymbolRateMap() {
                return Map.of("A-B", 22.0, "B-C", 11.2, "AC", 15.0);
            }
        };

        List<MidPrice> midPrices = rateSupplier.getMidPrices();

        Assertions.assertEquals(2, midPrices.size());
        Assertions.assertTrue(midPrices.contains(new MidPrice("A-B", 22.0)));
        Assertions.assertTrue(midPrices.contains(new MidPrice("B-C", 11.2)));
    }
}
