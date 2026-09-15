/* All server text is inserted via .text(), never interpreted as HTML. */
$(function () {
    let busy = false;
    let queued = false;
    let revision = 0;
    const number = (value, unit = '') => value === null || value === undefined || value === '' ? '—' : Number(value).toLocaleString('de-AT', {maximumFractionDigits: 1}) + (unit ? ' ' + unit : '');
    const time = value => value ? new Date(value).toLocaleString('de-AT') : '—';
    const badge = level => $('<span>').addClass('badge').addClass(['NORMAL', 'WARNING', 'CRITICAL', 'UNKNOWN'].includes(level) ? level : 'UNKNOWN').text(level || 'UNKNOWN');
    function xmlObject(element) {
        const result = {};
        $(element).children().each(function () { result[this.nodeName] = this.children.length ? xmlObject(this) : this.textContent; });
        return result;
    }
    async function request(path, collection) {
        const format = $('#format').val();
        const data = await $.ajax({url: '/api/v1' + path, headers: {Accept: 'application/' + format}, dataType: format, timeout: 8000});
        if (!data) return null;
        if (format === 'json') return collection ? data[collection] : data;
        if (!collection) return xmlObject(data.documentElement);
        return Array.from(data.documentElement.children).map(xmlObject);
    }
    function renderCards(stations, latest) {
        $('#stations').empty();
        stations.forEach((station, index) => {
            const measurement = latest[index];
            const card = $('<button>').addClass('station-card').toggleClass('selected', station.id === $('#station').val()).attr('aria-pressed', station.id === $('#station').val());
            card.append($('<div>').addClass('station-top').append($('<span>').addClass('river').text(station.river + ' / ' + station.id), badge(measurement?.warningLevel)));
            card.append($('<h3>').text(station.name), $('<p>').text('Pegelstation · ' + (String(station.isActive) === 'true' ? 'aktiv' : 'deaktiviert')));
            card.append($('<div>').addClass('station-number').text(number(measurement?.waterLevel)).append($('<small>').text(' cm')));
            card.on('click', () => { $('#station').val(station.id); revision++; refresh(); });
            $('#stations').append(card);
        });
    }
    async function refresh() {
        if (busy) { queued = true; return; }
        busy = true;
        const currentRevision = revision;
        $('#refresh').prop('disabled', true);
        $('#message').removeClass('error').text('Messdaten werden geladen …');
        try {
            const stations = await request('/stations', 'stations');
            if (currentRevision !== revision) { queued = true; return; }
            const selected = $('#station').val();
            $('#station').empty();
            stations.forEach(s => $('#station').append($('<option>').val(s.id).text(s.name)));
            if (stations.some(s => s.id === selected)) $('#station').val(selected);
            $('#station-count').text(stations.length + ' Stationen im Messnetz');
            if (!stations.length) {
                $('#stations, #latest, #statistics, #history').empty();
                $('#current-name').text('—'); $('#row-count').text('0 Messungen');
                $('#message').text('Keine Stationen vorhanden.'); return;
            }
            const id = $('#station').val();
            const query = new URLSearchParams();
            ['from', 'to'].forEach(key => { if ($('#' + key).val()) query.set(key, new Date($('#' + key).val()).toISOString()); });
            if ($('#warning').val()) query.set('warningLevel', $('#warning').val());
            const [latest, history, stats] = await Promise.all([
                Promise.all(stations.map(s => request('/stations/' + encodeURIComponent(s.id) + '/measurements/latest'))),
                request('/stations/' + encodeURIComponent(id) + '/measurements?' + query + '&limit=100', 'measurements'),
                request('/stations/' + encodeURIComponent(id) + '/statistics?' + query)
            ]);
            if (currentRevision !== revision) { queued = true; return; }
            renderCards(stations, latest);
            const station = stations.find(s => s.id === id);
            const measurement = latest[stations.findIndex(s => s.id === id)];
            $('#current-name').text(station.name);
            $('#latest').empty();
            if (measurement) {
                $('#latest').append($('<div>').addClass('big-level').text(number(measurement.waterLevel)).append($('<small>').text(' cm')), badge(measurement.warningLevel));
                $('#latest').append($('<p>').addClass('latest-meta').text(time(measurement.timestamp) + ' · ' + measurement.status + ' · ' + number(measurement.temperature, '°C')));
            } else $('#latest').text('Noch keine Messung vorhanden · UNKNOWN');
            const metrics = [['Minimum', stats.minWaterLevel, 'cm'], ['Maximum', stats.maxWaterLevel, 'cm'], ['Ø Wasserstand', stats.averageWaterLevel, 'cm'], ['Ø Durchfluss', stats.averageFlowRate, 'm³/s'], ['Σ Stundenwerte', stats.totalRainfall, 'mm/h'], ['Messungen', stats.count, ''], ['Warnungen', stats.warningCount, ''], ['Kritisch', stats.criticalCount, '']];
            const grid = $('<div>').addClass('stat-grid');
            metrics.forEach(([label, value, unit]) => grid.append($('<div>').append($('<div>').addClass('stat-label').text(label), $('<div>').addClass('stat-value').text(number(value, unit)))));
            $('#statistics').empty().append(grid);
            $('#history').empty();
            history.forEach(m => {
                const row = $('<tr>').append($('<td>').text(station.name).append($('<small>').text(time(m.timestamp))));
                [number(m.waterLevel, 'cm'), number(m.flowRate, 'm³/s'), number(m.rainfall, 'mm/h'), number(m.batteryLevel, '%'), m.status].forEach(value => row.append($('<td>').text(value)));
                row.append($('<td>').append(badge(m.warningLevel))); $('#history').append(row);
            });
            if (!history.length) $('#history').append($('<tr>').append($('<td>').attr('colspan', 7).text('Keine Messwerte für diese Filter.')));
            $('#row-count').text(history.length + ' Messungen');
            $('#updated').text('Stand ' + new Date().toLocaleTimeString('de-AT'));
            $('#message').text('Daten als ' + $('#format').val().toUpperCase() + ' geladen.');
        } catch (error) {
            if (currentRevision !== revision) { queued = true; return; }
            let message = error.responseJSON?.message;
            if (!message && error.responseText) {
                try { message = $($.parseXML(error.responseText)).find('message').first().text(); } catch (_) { /* not XML */ }
            }
            $('#message').addClass('error').text(message || 'Daten konnten nicht geladen werden. Verbindung und Filter prüfen.');
        } finally {
            busy = false; $('#refresh').prop('disabled', false);
            if (queued) { queued = false; refresh(); }
        }
    }
    $('#refresh').on('click', refresh);
    $('#station, #format, #warning, #from, #to').on('change', () => { revision++; refresh(); });
    refresh();
    setInterval(() => { if (!document.hidden) refresh(); }, 10000);
});
