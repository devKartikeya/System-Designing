const express = require("express");

const app = express();
const PORT = 3002;
const USERS_SERVICE_URL = "http://localhost:3001";

app.use(express.json());

app.post("/payments", async (req, res) => {
  const { userId, amount } = req.body;

  if (!userId || typeof amount !== "number" || amount <= 0) {
    return res.status(400).json({
      message: "Valid userId and positive amount are required"
    });
  }

  try {
    const response = await fetch(
      `${USERS_SERVICE_URL}/users/${userId}`
    );

    if (response.status === 404) {
      return res.status(404).json({
        message: "Cannot process payment: user not found"
      });
    }

    if (!response.ok) {
      return res.status(502).json({
        message: "Users Service returned an error"
      });
    }

    const user = await response.json();

    // Demo only: no real payment is processed.
    res.json({
      message: "Demo payment successful",
      user: user.name,
      amount
    });
  } catch (error) {
    res.status(503).json({
      message: "Users Service is unavailable"
    });
  }
});

app.listen(PORT, () => {
  console.log(`Payment Service running on port ${PORT}`);
});