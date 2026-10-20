let selectedQRId = "";

function showAlert(type, message) {
    const b = document.getElementById('alertBox');
    b.className = 'alert-msg alert-' + type;
    b.innerHTML = '<i class="fas fa-' + (type === 'success' ? 'check-circle' : 'exclamation-circle') + '"></i> ' + message;
    b.style.display = 'flex';
    setTimeout(() => b.style.display = 'none', 4000);
}

function loadQRs() {
    fetch('QRCodeServlet?action=list', { credentials: 'include' })
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById('qrTableBody');
            tbody.innerHTML = '';
            if (data.length === 0) {
                tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:40px;color:#999;">
                    <i class="fas fa-qrcode" style="font-size:48px;opacity:0.3;display:block;margin-bottom:15px;"></i>
                    No QR codes found. Click "Generate New QR" to create one.</td></tr>`;
                return;
            }
            data.forEach(qr => {
                let badge = 'badge-unused', st = qr.status;
                if (qr.status === 'USED')     { badge = 'badge-used';     st = 'Used'; }
                if (qr.status === 'INACTIVE') { badge = 'badge-inactive'; st = 'Inactive'; }
                if (qr.status === 'UNUSED')   st = 'Unused';
                const row = document.createElement('tr');
                row.innerHTML = `
                    <td><input type="checkbox" class="qr-checkbox" value="${qr.qr_id}"></td>
                    <td><strong>#${qr.qr_id}</strong></td>
                    <td><code style="background:#f5f5f5;padding:4px 8px;border-radius:4px;font-size:12px;">${qr.qr_code.substring(0,20)}...</code></td>
                    <td><span class="badge-status ${badge}">${st}</span></td>
                    <td>${formatDate(qr.generated_date)}</td>
                    <td>
                        <button class="btn-action btn-view"     onclick="viewQR(${qr.qr_id})"><i class="fas fa-eye"></i></button>
                        <button class="btn-action btn-download" onclick="downloadQR(${qr.qr_id})"><i class="fas fa-download"></i></button>
                    </td>`;
                tbody.appendChild(row);
            });
        })
        .catch(err => { console.error('Error:', err); showAlert('error', 'Failed to load QR codes.'); });
}

function generateQR() {
    fetch('QRCodeServlet?action=generate', { method: 'POST', credentials: 'include' })
        .then(r => r.json())
        .then(data => { if (data.success) { showAlert('success', 'QR Code generated!'); loadQRs(); } else showAlert('error', 'Failed to generate.'); })
        .catch(() => showAlert('error', 'Error generating QR code.'));
}

function publishQR()    { bulkAction('publish',    'Published',   'Mark selected QR codes as USED?'); }
function deactivateQR() { bulkAction('deactivate', 'Deactivated', 'Deactivate selected QR codes?'); }
function deleteQR()     { bulkAction('delete',     'Deleted',     'Permanently delete selected QR codes?'); }

function bulkAction(action, name, msg) {
    const ids = Array.from(document.querySelectorAll('.qr-checkbox:checked')).map(cb => cb.value);
    if (ids.length === 0) { showAlert('error', 'Please select at least one QR code'); return; }
    if (!confirm(msg)) return;
    const body = new URLSearchParams();
    ids.forEach(id => body.append('ids[]', id));
    fetch('QRCodeServlet?action=' + action, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body })
        .then(r => r.json())
        .then(data => {
            if (data.success) { showAlert('success', `${ids.length} QR code(s) ${name.toLowerCase()} successfully!`); document.getElementById('selectAll').checked = false; loadQRs(); }
            else showAlert('error', data.message || 'Action failed.');
        })
        .catch(() => showAlert('error', 'Error performing action.'));
}

function viewQR(id)        { selectedQRId = id; document.getElementById('modalQRImage').src = 'QRCodeServlet?action=download&id=' + id; document.getElementById('qrModal').style.display = 'block'; }
function downloadModalQR() { window.location.href = 'QRCodeServlet?action=download&id=' + selectedQRId; }
function downloadQR(id)    { window.location.href = 'QRCodeServlet?action=download&id=' + id; }
function toggleSelectAll(cb) { document.querySelectorAll('.qr-checkbox').forEach(c => c.checked = cb.checked); }
function closeModal() { document.getElementById('qrModal').style.display = 'none'; }
window.onclick = function (e) { if (e.target === document.getElementById('qrModal')) closeModal(); };

window.onload = function () { loadQRs(); };