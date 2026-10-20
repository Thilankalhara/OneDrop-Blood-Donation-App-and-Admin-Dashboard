let historyData = [], filteredData = [];

fetch("QRHistoryServlet", { credentials: 'include' })
    .then(res => res.json())
    .then(data => { historyData = data; filteredData = [...data]; updateStats(); render(filteredData); })
    .catch(err => {
        console.error('Error:', err);
        document.getElementById('historyBody').innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-exclamation-triangle" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
            Failed to load scan history. Please refresh.</td></tr>`;
    });

function updateStats() {
    document.getElementById('totalScans').textContent   = historyData.length;
    document.getElementById('successScans').textContent = historyData.filter(r => r.scanStatus === 'SUCCESS').length;
    document.getElementById('failedScans').textContent  = historyData.filter(r => r.scanStatus === 'FAILED').length;
}

function render(data) {
    const tb = document.getElementById("historyBody");
    if (data.length === 0) {
        tb.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;color:#999;">
            <i class="fas fa-inbox" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
            No scan records found.</td></tr>`;
        return;
    }
    tb.innerHTML = data.map(r => `<tr>
        <td><strong>#${r.scanId}</strong></td>
        <td><strong>${r.userName}</strong></td>
        <td>${r.email}</td>
        <td><code style="background:#f5f5f5;padding:4px 8px;border-radius:4px;font-size:12px;">${r.qrCode.substring(0,20)}...</code></td>
        <td><span class="badge-status ${r.scanStatus === 'SUCCESS' ? 'badge-success' : 'badge-failed'}">${r.scanStatus}</span></td>
        <td>${formatDate(r.scannedAt)}</td>
    </tr>`).join('');
}

const emailSearch     = document.getElementById('emailSearch');
const filterType      = document.getElementById('filterType');
const filterDateStart = document.getElementById('filterDateStart');
const filterDateEnd   = document.getElementById('filterDateEnd');
const statusFilter    = document.getElementById('statusFilter');

emailSearch.addEventListener('input',    applyFilters);
statusFilter.addEventListener('change',  applyFilters);
filterDateStart.addEventListener('change', applyFilters);
filterDateEnd.addEventListener('change',   applyFilters);
filterType.addEventListener('change', function () {
    filterDateStart.style.display = this.value !== 'all' ? 'inline-block' : 'none';
    filterDateEnd.style.display   = this.value === 'custom' ? 'inline-block' : 'none';
    applyFilters();
});

function applyFilters() {
    const q = emailSearch.value.toLowerCase(), type = filterType.value;
    const start = filterDateStart.value, end = filterDateEnd.value, status = statusFilter.value;
    filteredData = historyData.filter(r => {
        const ms = !q || r.email.toLowerCase().includes(q) || r.userName.toLowerCase().includes(q);
        const ss = !status || r.scanStatus === status;
        let ds = true;
        if (type !== 'all' && r.scannedAt) {
            const d = new Date(r.scannedAt);
            if (type === 'today')  ds = new Date().toISOString().split('T')[0] === d.toISOString().split('T')[0];
            else if (type === 'month'  && start) { const [y,m]=start.split('-'); ds=d.getFullYear()==y&&(d.getMonth()+1)==m; }
            else if (type === 'year'   && start) ds = d.getFullYear() == start.split('-')[0];
            else if (type === 'custom' && start && end) { const s=new Date(start),e=new Date(end); e.setHours(23,59,59,999); ds=d>=s&&d<=e; }
        }
        return ms && ss && ds;
    });
    render(filteredData);
}

function resetFilters() {
    emailSearch.value=''; filterType.value='all'; filterDateStart.value=''; filterDateEnd.value=''; statusFilter.value='';
    filterDateStart.style.display='none'; filterDateEnd.style.display='none';
    filteredData=[...historyData]; render(filteredData);
}