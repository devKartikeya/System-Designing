const express = require("express");

const app = express();
const PORT = 3001;

app.use(express.json());

const users = [
  { id: 1, name: "Kartikeya" }
];

app.get("/users", (req, res) => {
  res.json(users);
});

app.get("/users/:id", (req, res) => {
  const user = users.find(
    (user) => user.id === Number(req.params.id)
  );

  if (!user) {
    return res.status(404).json({ message: "User not found" });
  }

  res.json(user);
});

app.listen(PORT, () => {
  console.log(`Users Service running on port ${PORT}`);
});