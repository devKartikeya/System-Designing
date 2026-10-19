const express = require("express")
const router = express.Router();

router.get("/", (req, res) => {
    res.send("Hello from Payment route !");
});

module.exports = router;