# Exit Strategy Service

This service provides an exit strategy management system that keeps orders in memory and updates them to MongoDB every 10 seconds. It supports both stop loss and target-based exits.

## Features

- **In-Memory Order Management**: Orders are kept in memory for fast access and processing
- **Periodic Database Updates**: Orders are updated to MongoDB every 10 seconds (not on every tick)
- **Stop Loss & Target Support**: Supports both stop loss and target-based exits
- **REST API**: Order creation and status queries only
- **Fully Automated Exit Processing**: Automatically checks and processes exits based on current market conditions
- **Live Profit/Loss Logging**: Shows current P&L for each tick in logs
- **Single Order Rule**: Only one active order allowed at a time
- **Realistic Option Pricing**: Entry price = 1% of index price, LTP = Entry + Index Movement

## Components

### 1. JtradeOrder (MongoDB Document)
- Collection: `jtrade_orders`
- Stores order information including entry/exit details, stop loss, target, and status

### 2. ExitStrategyService
- Manages orders in memory
- Handles order creation, exit processing, and database updates
- Integrates with tick processing for automatic exit checks
- Supports realistic option pricing (1% of index price)

### 3. ExitStrategySchedulerService
- Runs every 10 seconds to update orders to database
- Ensures data persistence without overwhelming the database

### 4. ExitStrategyController
- REST API endpoints for order management
- Supports order creation, exit, and status queries
- Supports realistic option pricing endpoints

## API Endpoints

### Create Order
```http
POST /api/exit-strategy/order
Content-Type: application/json

{
  "orderType": "CALL_BUY",
  "tradingSymbol": "NIFTY24JAN19000CE",
  "instrumentToken": 123456,
  "entryPrice": 150.0,
  "entryIndexPrice": 19000.0,
  "stopLossPrice": 120.0,
  "targetPrice": 200.0,
  "quantity": 50
}
```

### Create Realistic Order (1% of Index Price)
```http
POST /api/exit-strategy/order/realistic
Content-Type: application/json

{
  "orderType": "CALL_BUY",
  "tradingSymbol": "NIFTY24JAN19000CE",
  "instrumentToken": 123456,
  "currentIndexPrice": 19000.0,
  "stopLossPercentage": 0.5,
  "targetPercentage": 1.0,
  "quantity": 50
}
```



### Get Active Orders
```http
GET /api/exit-strategy/orders/active
```

### Get Active Orders by Type
```http
GET /api/exit-strategy/orders/active/CALL_BUY
```



### Force Database Update
```http
POST /api/exit-strategy/update-database
```

## Usage Examples

### Creating an Order Entry
```java
@Autowired
private ExitStrategyService exitStrategyService;

// Create a CALL BUY order
JtradeOrder order = exitStrategyService.createOrderEntry(
    OrderTypeEnum.CALL_BUY,
    "NIFTY24JAN19000CE",
    123456L,
    150.0,  // entry price
    19000.0, // entry index price
    120.0,   // stop loss
    200.0,   // target
    50       // quantity
);
```

### Creating a Realistic Order Entry (1% of Index Price)
```java
// Create a CALL BUY order with realistic pricing
JtradeOrder order = exitStrategyService.createOrderEntryWithRealisticPricing(
    OrderTypeEnum.CALL_BUY,
    "NIFTY24JAN19000CE",
    123456L,
    19000.0, // current index price
    0.5,     // stop loss percentage
    1.0,     // target percentage
    50       // quantity
);

// This will automatically calculate:
// - Entry Price = 1% of 19000 = 190.0
// - Stop Loss = 190 * (1 - 0.5%) = 189.05
// - Target = 190 * (1 + 1.0%) = 191.90
```



### Live Profit/Loss Logging
```java
// This is automatically called during tick processing
// Shows live P&L for each active order in logs
```

## Integration with Tick Processing

The exit strategy service is automatically integrated with the tick processing pipeline:

1. **Tick Processing**: During each tick, the service checks if any active orders need to be exited
2. **Live P&L Logging**: Shows current profit/loss for each active order in logs
3. **Realistic LTP Calculation**: Current LTP = Entry Price + (Current Index Price - Entry Index Price)
4. **Automatic Exits**: Orders are automatically exited when stop loss or target conditions are met
5. **Database Updates**: Orders are updated to MongoDB every 10 seconds via scheduler
6. **Single Order Rule**: Only one active order allowed at a time



## Realistic Option Pricing

The service implements realistic option pricing simulation:

### Entry Price Calculation
- **Entry Price** = 1% of current index price
- Example: If Nifty is at 19000, entry price = 190.0

### LTP (Latest Trading Price) Calculation
- **LTP** = Entry Price + (Current Index Price - Entry Index Price)
- Example: If index moves from 19000 to 19100 (+100 points)
- LTP = 190 + 100 = 290.0

### Stop Loss & Target
- **Stop Loss**: Percentage-based on entry price
- **Target**: Percentage-based on entry price
- Automatically calculated based on order type (CALL/PUT)

### Live Profit/Loss Logging
- **Every Tick**: Current P&L is logged for each active order
- **Log Format**: `LIVE P&L - CALL_BUY NIFTY24JAN19000CE: Entry=190.0, Current LTP=195.0, P&L=5.0, Total P&L=250.0, Index=19100`
- **Real-time Monitoring**: Track profit/loss without manual intervention

## Order Status

- **ACTIVE**: Order is currently active and being monitored
- **EXITED**: Order has been exited (either stop loss, target, or manual exit)
- **CANCELLED**: Order has been cancelled

## Exit Reasons

- **STOPLOSS_HIT**: Stop loss condition was met
- **TARGET_HIT**: Target condition was met
- **FORCE_EXIT**: Manual exit or other forced exit
- **EXIT_SIGNAL**: Exit signal from strategy
- **MAX_DAY_LOSS_REACHED**: Maximum daily loss limit reached
- **MAX_DAY_PROFIT_REACHED**: Maximum daily profit limit reached

## Configuration

The service uses the following configuration:

- **Database Updates**: Every 10 seconds (configurable in ExitStrategySchedulerService)
- **MongoDB Collection**: `jtrade_orders`
- **Memory Management**: ConcurrentHashMap for thread-safe in-memory storage

## Performance Considerations

- **Memory Usage**: Orders are kept in memory for fast access
- **Database Load**: Updates happen every 10 seconds, not on every tick
- **Thread Safety**: Uses ConcurrentHashMap for thread-safe operations
- **Error Handling**: Comprehensive error handling with logging

## Monitoring

The service provides detailed logging for:
- Order creation and exits
- Database update operations
- Error conditions
- Performance metrics

Check the application logs for detailed information about order processing and any issues.
