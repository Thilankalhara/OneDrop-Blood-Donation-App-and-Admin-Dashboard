/* Back/forward cache block */
if (window.history && window.history.pushState) {
    window.history.pushState(null, null, document.URL);
    window.addEventListener('popstate', function () {
        window.location.href = "admin_login.html";
    });
}

/* Load admin info into top bar */
function loadAdminInfo() {
    fetch('AdminInfoServlet', { credentials: 'include' })
        .then(response => response.json())
        .then(data => {
            if (data.error) {
                console.warn('Not logged in:', data.error);
                window.location.href = 'admin_login.html';
                return;
            }
            document.querySelectorAll('.admin-name').forEach(el => el.textContent = data.name);
            document.querySelectorAll('.admin-email').forEach(el => el.textContent = data.email);
            document.querySelectorAll('.admin-avatar').forEach(el => el.textContent = data.initial);
        })
        .catch(err => {
            console.error('Error fetching admin info:', err);
            window.location.href = 'admin_login.html';
        });
}

/* Highlight active sidebar link */
function highlightActiveSidebar() {
    const currentPage = window.location.pathname.split("/").pop();
    document.querySelectorAll(".sidebar .nav-item").forEach(item => {
        const href = item.getAttribute("href");
        if (href === currentPage) item.classList.add("active");
        else item.classList.remove("active");
    });
}

/* Logout confirmation */
function confirmLogout(event) {
    if (!confirm("Are you sure you want to log out?")) {
        event.preventDefault();
        return false;
    }
    return true;
}

/* Format date */
function formatDate(dateStr) {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-US', {
        year: 'numeric', month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit'
    });
}

window.addEventListener('DOMContentLoaded', function () {
    loadAdminInfo();
    highlightActiveSidebar();
});