<!DOCTYPE html>
<html>
<head><title>Greeting Page</title></head>
<body>
    <h2>Enter your name:</h2>
    <form action="GreetingServlet" method="POST">
        <input type="text" name="userName" required>
        <input type="submit" value="Say Hello">
    </form>
</body>
</html>