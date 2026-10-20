if (window.history && window.history.pushState) {
    window.history.pushState(null, null, document.URL);
    window.addEventListener('popstate', function () {
        window.location.href = "admin_login.html";
    });
}
if (window.history && window.history.replaceState) {
    window.history.replaceState(null, null, window.location.href);
}

function togglePassword() {
    const input = document.getElementById("password");
    const icon  = document.getElementById("toggleIcon");
    if (input.type === "password") {
        input.type = "text";
        icon.classList.remove("fa-eye");
        icon.classList.add("fa-eye-slash");
    } else {
        input.type = "password";
        icon.classList.remove("fa-eye-slash");
        icon.classList.add("fa-eye");
    }
}

function submitLogin(event) {
    event.preventDefault();
    const email    = document.getElementById("email").value;
    const password = document.getElementById("password").value;
    fetch('AdminLoginServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'email=' + encodeURIComponent(email) + '&password=' + encodeURIComponent(password)
    })
    .then(res => res.text())
    .then(data => {
        if (data.trim() === "success") window.location.href = "admin_panel.html";
        else alert("Invalid Email or Password");
    })
    .catch(err => console.log(err));
}