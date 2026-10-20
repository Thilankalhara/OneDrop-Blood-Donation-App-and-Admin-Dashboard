window.addEventListener('DOMContentLoaded', function () {
    fetch('AdminDashboardServlet', { credentials: 'include' })
        .then(res => res.json())
        .then(data => {
            document.getElementById('statUsers').innerText     = data.totalUsers     || 0;
            document.getElementById('statDonations').innerText = data.totalDonations || 0;
            document.getElementById('statQR').innerText        = data.totalQRCodes   || 0;
            document.getElementById('statCenters').innerText   = data.totalCenters   || 0;
        })
        .catch(err => console.error('Dashboard stats error:', err));
});