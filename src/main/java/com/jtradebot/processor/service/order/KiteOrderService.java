package com.jtradebot.processor.service.order;

import com.jtradebot.processor.common.ProfileUtil;
import com.jtradebot.processor.repository.JtradeOrderRepository;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.repository.document.TradeOrder;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;

import static com.jtradebot.processor.handler.DateTimeHandler.getCurrentISTTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class KiteOrderService {
    private final JtradeOrderRepository jtradeOrderRepository;
    private final KiteConnect kiteConnect;
    private final Environment environment;

    public void placeOrder(JtradeOrder jtradeOrder, String orderType) {
        try {
            if(!ProfileUtil.isProfileActive(environment, "live")) {
                log.warn("Not in live profile. Skipping order placement for order ID: {}", jtradeOrder.getId());
                return;
            }
            log.info("Placing order with JTradeOrder: {} with orderType: {}", jtradeOrder, orderType);
            OrderParams orderParams = getEntryExitOrderParams(jtradeOrder, orderType);
            Order placedOrder = kiteConnect.placeOrder(orderParams, Constants.VARIETY_REGULAR);

            if (placedOrder != null) {
                jtradeOrder.setKiteOrderId(placedOrder.orderId);
                jtradeOrder.setKiteOrderStatus(placedOrder.status);
                jtradeOrder.setLastUpdated(getCurrentISTTime());
                jtradeOrderRepository.save(jtradeOrder);
                log.info("🛒 ORDER PLACED - ID: {}, Kite Order ID: {}, Order Type: {}, Status: {}, Qty: {}, Price: {}",
                        jtradeOrder.getId(), placedOrder.orderId, orderType, placedOrder.status,
                        placedOrder.quantity, placedOrder.price);
            } else {
                log.error("Order placement failed for order ID: {}", jtradeOrder.getId());
            }
        } catch (KiteException | IOException e) {
            throw new RuntimeException("Unable to place order.", e);
        }
    }

    private static OrderParams getEntryExitOrderParams(JtradeOrder tradeOrder, String orderType) {
        OrderParams orderParams = new OrderParams();
        orderParams.transactionType = Constants.TRANSACTION_TYPE_BUY;
        orderParams.exchange = Constants.EXCHANGE_NFO;
        orderParams.tradingsymbol = tradeOrder.getTradingSymbol();
        orderParams.orderType = Constants.ORDER_TYPE_MARKET;
        orderParams.product = Constants.PRODUCT_MIS;
        orderParams.quantity = tradeOrder.getQuantity();
        return orderParams;
    }

}
