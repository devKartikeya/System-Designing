const express = require('express');
const app = express();
const PORT = process.env.PORT || 3000;
const usersRouter = require("./users/users.route");
const paymentRouter = require("./payment/payment.route");

// Middleware
app.use(express.urlencoded({ extended: true }));
app.use(express.json());

app.use("/users", usersRouter);
app.use("/payment", paymentRouter);

// Basic route
app.get('/', (req, res) => {
  res.send('Hello from Express backend!');
});

// Start server
app.listen(PORT, () => {
  console.log(`Server running on http://localhost:PORT`);
});