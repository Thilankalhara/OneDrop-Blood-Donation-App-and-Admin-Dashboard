let map    = L.map('map').setView([6.9271, 79.8612], 12);
let marker = L.marker([6.9271, 79.8612], { draggable: true }).addTo(map);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors' }).addTo(map);

marker.on('drag', function (e) {
    const pos = e.target.getLatLng();
    document.getElementById('latitude').value  = pos.lat.toFixed(6);
    document.getElementById('longitude').value = pos.lng.toFixed(6);
});
document.getElementById('latitude').addEventListener('input',  updateMarker);
document.getElementById('longitude').addEventListener('input', updateMarker);

function updateMarker() {
    const lat = parseFloat(document.getElementById('latitude').value);
    const lng = parseFloat(document.getElementById('longitude').value);
    if (!isNaN(lat) && !isNaN(lng)) { marker.setLatLng([lat, lng]); map.setView([lat, lng]); }
}

function loadCenters() {
    fetch('HospitalsServlet', { credentials: 'include' })
        .then(res => res.json())
        .then(data => {
            const tbody = document.getElementById('centersTableBody');
            tbody.innerHTML = '';
            data.forEach(c => {
                tbody.innerHTML += `<tr>
                    <td>${c.id}</td><td>${c.name}</td><td>${c.phone}</td>
                    <td>${c.latitude.toFixed(6)}</td><td>${c.longitude.toFixed(6)}</td>
                    <td><select onchange="updateStatus(${c.id}, this.value)">
                        <option value="ACTIVE"   ${c.status === 'ACTIVE'   ? 'selected' : ''}>ACTIVE</option>
                        <option value="INACTIVE" ${c.status === 'INACTIVE' ? 'selected' : ''}>INACTIVE</option>
                    </select></td>
                    <td><button class="btn-action btn-remove" onclick="removeCenter(${c.id})"><i class="fas fa-trash me-1"></i> Remove</button></td>
                </tr>`;
            });
        })
        .catch(err => console.error('Error loading centers:', err));
}

function addCenter() {
    const name   = document.getElementById('centerName').value;
    const phone  = document.getElementById('centerPhone').value;
    const lat    = document.getElementById('latitude').value;
    const lng    = document.getElementById('longitude').value;
    const status = document.getElementById('hospitalStatus').value;
    if (!name || !phone || !lat || !lng) { alert("Fill all fields"); return; }
    fetch('HospitalsServlet', {
        method: 'POST', credentials: 'include',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `name=${encodeURIComponent(name)}&phone=${encodeURIComponent(phone)}&lat=${lat}&lng=${lng}&status=${status}`
    }).then(r => r.text()).then(data => { if (data.trim() === 'success') { alert("Center added!"); loadCenters(); } else alert("Failed to add"); });
}

function removeCenter(id) {
    if (!confirm("Delete this center?")) return;
    fetch('HospitalsServlet?id=' + id, { method: 'DELETE', credentials: 'include' })
        .then(r => r.text()).then(data => { if (data.trim() === 'success') loadCenters(); else alert("Failed to remove"); });
}

function updateStatus(id, status) {
    fetch(`HospitalsServlet?id=${id}&status=${status}`, { method: 'PUT', credentials: 'include' })
        .then(r => r.text()).then(data => { if (data.trim() !== 'success') alert("Failed to update status"); });
}

document.addEventListener('DOMContentLoaded', loadCenters);