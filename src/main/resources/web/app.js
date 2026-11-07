"use strict";

const $ = (id) => document.getElementById(id);

const CATEGORIES = {
    top: { color: "#6250d6", name: "функції" },
    decl: { color: "#1b9a8a", name: "оголошення" },
    stmt: { color: "#d9822b", name: "оператори" },
    expr: { color: "#2a78d6", name: "вирази" },
    literal: { color: "#6b7a3a", name: "літерали" },
    error: { color: "#d03b3b", name: "помилка" },
};

const PHASES = { lexer: "лексер", parser: "парсер", semantic: "семантика", runtime: "виконання" };
const HIDDEN_ROLES = new Set(["item", "root", "arg", "var", "param"]);

const state = {
    result: null,
    traceFilter: "all",
    grammarLoaded: false,
    presetsLoaded: false,
    astDirty: false,
    marks: {},
    activeTab: "ast",
};

const ast = { svg: null, g: null, zoom: null, root: null };

function escapeHtml(text) {
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

async function api(path, options) {
    const response = await fetch(path, options);
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || response.statusText);
    return data;
}

const source = $("source");
const gutter = $("gutter");

function updateGutter() {
    const count = source.value.split("\n").length;
    let html = "";
    for (let i = 1; i <= count; i++) {
        const mark = state.marks[i];
        html += mark ? `<span class="${mark}">${i}</span>\n` : `${i}\n`;
    }
    gutter.innerHTML = html + "\n";
    gutter.scrollTop = source.scrollTop;
}

function updateCursor() {
    const before = source.value.slice(0, source.selectionStart).split("\n");
    $("cursor").textContent = `${before.length}:${before[before.length - 1].length + 1}`;
}

function goToLine(line, column) {
    if (!line || line < 1) return;
    const lines = source.value.split("\n");
    let start = 0;
    for (let i = 0; i < line - 1 && i < lines.length; i++) start += lines[i].length + 1;
    const text = lines[line - 1] || "";
    const from = start + Math.min(Math.max((column || 1) - 1, 0), text.length);
    source.focus();
    source.setSelectionRange(from, start + text.length);
    source.scrollTop = Math.max(0, (line - 6) * 20);
    updateCursor();
}

source.addEventListener("input", updateGutter);
source.addEventListener("scroll", () => { gutter.scrollTop = source.scrollTop; });
source.addEventListener("click", updateCursor);
source.addEventListener("keyup", updateCursor);
source.addEventListener("keydown", (event) => {
    if (event.key === "Tab") {
        event.preventDefault();
        const { selectionStart: s, selectionEnd: e } = source;
        source.value = source.value.slice(0, s) + "    " + source.value.slice(e);
        source.selectionStart = source.selectionEnd = s + 4;
        updateGutter();
    } else if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
        event.preventDefault();
        analyze();
    }
});

document.querySelectorAll("#tabs button").forEach((button) => {
    button.addEventListener("click", () => showTab(button.dataset.tab));
});

function showTab(name) {
    state.activeTab = name;
    document.querySelectorAll("#tabs button").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
    document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.id === "tab-" + name));
    if (name === "ast" && state.astDirty) renderAst();
    if (name === "grammar") loadGrammar();
    if (name === "conflicts") loadPresets();
}

async function loadExamples() {
    const list = await api("/api/examples");
    const select = $("examples");
    select.innerHTML = list.map((e) => `<option value="${e.name}">${escapeHtml(e.title)}</option>`).join("");
    select.addEventListener("change", () => loadExample(select.value));
    const wanted = new URLSearchParams(location.search).get("example");
    const initial = list.find((e) => e.name === wanted) || list[0];
    if (initial) {
        select.value = initial.name;
        await loadExample(initial.name);
    }
    const tab = location.hash.slice(1);
    if (document.querySelector(`#tabs button[data-tab="${tab}"]`)) showTab(tab);
}

async function loadExample(name) {
    const data = await api("/api/example?name=" + encodeURIComponent(name));
    source.value = data.text;
    state.marks = {};
    updateGutter();
    source.scrollTop = 0;
    await analyze();
}

async function analyze() {
    const button = $("analyze");
    button.disabled = true;
    button.textContent = "Аналіз...";
    try {
        const result = await api("/api/analyze", {
            method: "POST",
            headers: { "Content-Type": "text/plain; charset=utf-8" },
            body: source.value,
        });
        state.result = result;
        renderPipeline(result);
        renderDiagnostics(result);
        renderTokens(result);
        renderTrace(result);
        renderRun(result);
        renderJs(result);
        state.astDirty = true;
        if (state.activeTab === "ast") renderAst();
    } catch (error) {
        $("diagnostics").innerHTML = `<div class="diag"><span class="badge error">помилка</span><span></span><span>${escapeHtml(error.message)}</span></div>`;
    } finally {
        button.disabled = false;
        button.textContent = "Аналізувати";
    }
}

function ms(micros) {
    if (micros === undefined) return "";
    return micros < 1000 ? `${micros} мкс` : `${(micros / 1000).toFixed(1)} мс`;
}

function renderPipeline(r) {
    const has = (phase) => r.diagnostics.some((d) => d.phase === phase && d.severity === "error");
    const stages = [
        { name: "Лексичний аналіз", ok: !has("lexer"), skip: false, info: `${r.tokens.length} токенів · ${ms(r.timings.lexer)}` },
        { name: "Синтаксичний (LALR)", ok: r.syntaxOk, skip: has("lexer"), info: `${r.trace.total} кроків · ${ms(r.timings.parser)}` },
        { name: "Семантичний", ok: r.semanticOk, skip: !r.syntaxOk, info: ms(r.timings.semantic) },
        { name: "Генерація JS", ok: !!r.javascript, skip: !r.semanticOk, info: ms(r.timings.codegen) },
        {
            name: "Виконання",
            ok: r.run && !r.run.error,
            skip: !r.run,
            info: r.run ? `${r.run.millis} мс` : "",
        },
    ];
    $("pipeline").innerHTML = stages.map((s) => {
        const cls = s.skip ? "skip" : s.ok ? "ok" : "fail";
        const info = s.skip ? "" : `<span class="muted">${escapeHtml(s.info)}</span>`;
        return `<span class="stage ${cls}"><span class="dot"></span>${s.name} ${info}</span>`;
    }).join("");
}

function renderDiagnostics(r) {
    state.marks = {};
    for (const d of r.diagnostics) {
        if (d.line > 0 && state.marks[d.line] !== "bad") state.marks[d.line] = d.severity === "error" ? "bad" : "warn";
    }
    updateGutter();
    const box = $("diagnostics");
    if (!r.diagnostics.length) {
        box.innerHTML = `<div class="diag"><span class="badge ok">OK</span><span></span><span>Помилок і попереджень немає</span></div>`;
        return;
    }
    box.innerHTML = r.diagnostics.map((d, i) => `
        <div class="diag" data-i="${i}">
            <span><span class="badge ${d.severity}">${d.severity === "error" ? "помилка" : "увага"}</span></span>
            <span class="pos">${d.line > 0 ? d.line + ":" + d.column : "—"} ${PHASES[d.phase] || d.phase}</span>
            <span>${escapeHtml(d.message)}</span>
        </div>`).join("");
    box.querySelectorAll(".diag").forEach((row) => {
        row.addEventListener("click", () => {
            const d = r.diagnostics[+row.dataset.i];
            goToLine(d.line, d.column);
        });
    });
}

function renderTokens(r) {
    $("tokens-info").textContent = `Усього токенів: ${r.tokens.length}. Клік по рядку — перейти до лексеми в коді.`;
    const rows = r.tokens.map((t, i) => `
        <tr data-i="${i}"><td>${i + 1}</td><td class="mono term">${t.type}</td>
        <td class="mono">${escapeHtml(t.text)}</td><td class="mono">${t.line}:${t.column}</td></tr>`).join("");
    const table = $("tokens");
    table.innerHTML = `<thead><tr><th>#</th><th>Токен (термінал)</th><th>Лексема</th><th>Позиція</th></tr></thead><tbody>${rows}</tbody>`;
    table.querySelectorAll("tbody tr").forEach((row) => {
        row.addEventListener("click", () => {
            const t = r.tokens[+row.dataset.i];
            goToLine(t.line, t.column);
        });
    });
}

document.querySelectorAll("#trace-filter button").forEach((button) => {
    button.addEventListener("click", () => {
        state.traceFilter = button.dataset.filter;
        document.querySelectorAll("#trace-filter button").forEach((b) => b.classList.toggle("active", b === button));
        if (state.result) renderTrace(state.result);
    });
});

function tail(list, n) {
    if (list.length <= n) return list.join(" ");
    return "… " + list.slice(list.length - n).join(" ");
}

function renderTrace(r) {
    const steps = r.trace.steps;
    const counts = { shift: 0, reduce: 0, error: 0, accept: 0 };
    steps.forEach((s) => { counts[s.action] = (counts[s.action] || 0) + 1; });
    let info = `shift: ${counts.shift}, reduce: ${counts.reduce}, error: ${counts.error}`;
    if (r.trace.total > steps.length) info += ` · показано перші ${steps.length} з ${r.trace.total} кроків`;
    $("trace-info").textContent = info;
    const filtered = state.traceFilter === "all" ? steps : steps.filter((s) => s.action === state.traceFilter);
    const rows = filtered.map((s) => `
        <tr>
            <td>${s.n}</td>
            <td class="mono stack" title="${escapeHtml(s.states.join(" "))}">${escapeHtml(tail(s.states, 6))}</td>
            <td class="mono stack" title="${escapeHtml(s.symbols.join(" "))}">${escapeHtml(tail(s.symbols, 5))}</td>
            <td class="mono nowrap">${escapeHtml(s.input)}</td>
            <td class="action ${s.action}">${s.action}</td>
            <td class="mono">${escapeHtml(s.detail)}</td>
        </tr>`).join("");
    $("trace").innerHTML = `<thead><tr><th>#</th><th>Стек станів</th><th>Стек символів</th><th>Вхідний токен</th><th>Дія</th><th>Деталі</th></tr></thead><tbody>${rows}</tbody>`;
}

function renderRun(r) {
    const info = $("run-info");
    const output = $("run-output");
    if (!r.run) {
        const reason = !r.syntaxOk || !r.semanticOk
            ? "Програму не виконано: спочатку виправте помилки."
            : "Програму не виконано: немає функції main.";
        info.innerHTML = `<span class="badge warning">не виконувалось</span><span class="muted">${reason}</span>`;
        output.textContent = "";
        return;
    }
    const run = r.run;
    const chips = [];
    if (run.error) {
        chips.push(`<span class="badge error">помилка виконання</span>`);
        chips.push(`<span class="kv" style="cursor:pointer" id="run-error">рядок <b>${run.errorLine}</b>: ${escapeHtml(run.error)}</span>`);
    } else {
        chips.push(`<span class="badge ok">успішно</span>`);
        chips.push(`<span class="kv">main повернула <b>${run.exitCode}</b></span>`);
    }
    chips.push(`<span class="kv">кроків інтерпретатора <b>${run.steps.toLocaleString("uk-UA")}</b></span>`);
    chips.push(`<span class="kv">час <b>${run.millis} мс</b></span>`);
    info.innerHTML = chips.join("");
    output.textContent = run.output || "(порожній вивід)";
    const link = $("run-error");
    if (link) link.addEventListener("click", () => goToLine(run.errorLine, 1));
}

function highlightJs(code) {
    const pattern = /(&quot;(?:\\.|[^\\&]|&(?!quot;))*&quot;)|\b(function|return|let|const|if|else|for|while|do|break|continue|typeof|throw|new)\b|\b(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)\b|(__\w+|Math\.\w+)/g;
    return escapeHtml(code).replace(pattern, (m, str, kw, num, helper) => {
        if (str) return `<span class="hs">${str}</span>`;
        if (kw) return `<span class="hk">${kw}</span>`;
        if (num) return `<span class="hn">${num}</span>`;
        return `<span class="hh">${helper}</span>`;
    });
}

function renderJs(r) {
    $("js-output").hidden = true;
    $("js-compare").innerHTML = "";
    if (!r.javascript) {
        $("js-code").textContent = "// Код не згенеровано: програма містить помилки.";
        return;
    }
    const code = $("js-prelude").checked ? r.javascript : r.javascript.slice(r.preludeLength);
    $("js-code").innerHTML = highlightJs(code);
}

$("js-prelude").addEventListener("change", () => state.result && renderJs(state.result));

$("js-run").addEventListener("click", () => {
    const r = state.result;
    if (!r || !r.javascript) return;
    const box = $("js-output");
    if (r.run && r.run.error && /ліміт|тайм-аут/.test(r.run.error)) {
        box.hidden = false;
        box.textContent = "Інтерпретатор зупинив цю програму за лімітом кроків, тому в браузері вона не запускається (вкладка зависла б).";
        return;
    }
    const start = performance.now();
    let output;
    let error = null;
    try {
        output = new Function(r.javascript + "\nreturn __out.join(\"\");")();
    } catch (e) {
        error = e.message;
    }
    const time = (performance.now() - start).toFixed(1);
    box.hidden = false;
    box.textContent = error ? `Помилка: ${error}` : output || "(порожній вивід)";
    const expected = r.run ? r.run.output : null;
    let badge;
    if (error) badge = `<span class="badge error">помилка</span>`;
    else if (expected === null) badge = "";
    else if (expected === output) badge = `<span class="badge ok">збігається з інтерпретатором</span>`;
    else badge = `<span class="badge warning">відрізняється від інтерпретатора</span>`;
    $("js-compare").innerHTML = `${badge} <span class="muted">${time} мс у браузері</span>`;
});

$("js-download").addEventListener("click", () => {
    const r = state.result;
    if (!r || !r.javascript) return;
    const blob = new Blob([r.javascript], { type: "text/javascript" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = ($("examples").value || "program.c").replace(/\.c$/, "") + ".js";
    a.click();
    URL.revokeObjectURL(a.href);
});

async function loadGrammar() {
    if (state.grammarLoaded) return;
    state.grammarLoaded = true;
    const g = await api("/api/grammar");
    const terminals = new Set(g.terminals.concat(["EOF", "error"]));
    $("grammar-info").textContent = `${g.productions.length} продукцій · ${g.terminals.length} терміналів · ${g.nonterminals.length} нетерміналів · стартовий символ: ${g.start}`;
    $("precedence").innerHTML = `<thead><tr><th>Рівень</th><th>Асоціативність</th><th>Термінали</th></tr></thead><tbody>${
        g.precedence.map((line, i) => {
            const assoc = line.split(" ")[0];
            const names = line.slice(assoc.length).trim();
            return `<tr><td>${i + 1}</td><td>${assoc}</td><td class="mono term">${escapeHtml(names)}</td></tr>`;
        }).join("")}</tbody>`;
    const rows = g.productions.map((p) => {
        const rhs = p.rhs.length
            ? p.rhs.map((s) => `<span class="${terminals.has(s) ? "term" : "nonterm"}">${escapeHtml(s)}</span>`).join(" ")
            : `<span class="muted">ε</span>`;
        const text = (p.lhs + " " + p.rhs.join(" ")).toLowerCase();
        return `<tr data-text="${escapeHtml(text)}"><td>${p.index}</td><td class="mono nonterm">${escapeHtml(p.lhs)}</td>
            <td class="mono">::=</td><td class="mono">${rhs}</td><td class="mono term">${p.precedence ? "%prec " + p.precedence : ""}</td></tr>`;
    }).join("");
    $("productions").innerHTML = `<thead><tr><th>#</th><th>Нетермінал</th><th></th><th>Права частина</th><th></th></tr></thead><tbody>${rows}</tbody>`;
    $("cup-source").textContent = g.cup;
    $("flex-source").textContent = g.flex;
}

$("grammar-filter").addEventListener("input", (event) => {
    const q = event.target.value.trim().toLowerCase();
    document.querySelectorAll("#productions tbody tr").forEach((row) => {
        row.hidden = q && !row.dataset.text.includes(q);
    });
});

async function loadPresets() {
    if (state.presetsLoaded) return;
    state.presetsLoaded = true;
    const list = await api("/api/conflict-presets");
    const select = $("conflict-presets");
    select.innerHTML = list.map((p) => `<option value="${p.name}" data-expect="${p.expect}">${escapeHtml(p.title)}</option>`).join("");
    select.addEventListener("change", () => loadPreset(select.value));
    if (list.length) await loadPreset(list[0].name);
}

async function loadPreset(name) {
    const data = await api("/api/conflict-preset?name=" + encodeURIComponent(name));
    $("conflict-grammar").value = data.text;
    const option = $("conflict-presets").selectedOptions[0];
    $("conflict-expect").value = option ? option.dataset.expect : 0;
    await runConflicts();
}

async function runConflicts() {
    const button = $("conflict-run");
    button.disabled = true;
    button.textContent = "CUP працює...";
    try {
        const params = new URLSearchParams({
            expect: $("conflict-expect").value || "0",
            states: $("conflict-states").checked ? "1" : "0",
            tables: $("conflict-tables").checked ? "1" : "0",
        });
        const r = await api("/api/conflicts?" + params, {
            method: "POST",
            headers: { "Content-Type": "text/plain; charset=utf-8" },
            body: $("conflict-grammar").value,
        });
        const total = r.shiftReduce + r.reduceReduce;
        $("conflict-summary").innerHTML = [
            total ? `<span class="badge error">конфліктів: ${total}</span>` : `<span class="badge ok">конфліктів немає</span>`,
            `<span class="kv">shift-reduce <b>${r.shiftReduce}</b></span>`,
            `<span class="kv">reduce-reduce <b>${r.reduceReduce}</b></span>`,
            r.states >= 0 ? `<span class="kv">станів LALR <b>${r.states}</b></span>` : "",
            r.generated ? `<span class="badge ok">парсер згенеровано</span>` : `<span class="badge error">парсер не згенеровано</span>`,
        ].join("");
        $("conflict-output").innerHTML = escapeHtml(r.output).split("\n").map((line) => {
            if (/conflict/i.test(line) && !/^\s*0 /.test(line.trim()) && !/\b0 conflicts\b/.test(line)) return `<span class="hl-conflict">${line}</span>`;
            if (/Resolved in favor|between/.test(line)) return `<span class="hl-conflict">${line}</span>`;
            return line;
        }).join("\n");
    } catch (error) {
        $("conflict-output").textContent = error.message;
    } finally {
        button.disabled = false;
        button.textContent = "Запустити CUP";
    }
}

$("conflict-run").addEventListener("click", runConflicts);
$("analyze").addEventListener("click", analyze);

const measureContext = document.createElement("canvas").getContext("2d");

function textWidth(text, font) {
    measureContext.font = font;
    return measureContext.measureText(text).width;
}

function nodeSize(d) {
    const label = textWidth(d.data.label, "600 12px Consolas, monospace");
    const type = d.data.type ? textWidth(": " + d.data.type, "10.5px Consolas, monospace") : 0;
    return { w: Math.max(34, Math.max(label, type) + 22), h: d.data.type ? 36 : 24 };
}

function colorOf(d) {
    return (CATEGORIES[d.data.category] || CATEGORIES.expr).color;
}

function hiddenCount(d) {
    const kids = d.children || d._children || [];
    return kids.reduce((n, c) => n + 1 + hiddenCount(c), 0);
}

function renderLegend() {
    $("legend").innerHTML = Object.values(CATEGORIES)
        .map((c) => `<span><i style="background:${c.color}"></i>${c.name}</span>`).join("");
}

function renderAst() {
    state.astDirty = false;
    const container = $("ast");
    container.innerHTML = "";
    const data = state.result && state.result.ast;
    if (!data) {
        container.innerHTML = `<p style="padding:16px" class="muted">AST не побудовано: розбір завершився помилкою.</p>`;
        return;
    }
    const svg = d3.select(container).append("svg");
    const g = svg.append("g");
    const zoom = d3.zoom().scaleExtent([0.08, 3]).on("zoom", (event) => g.attr("transform", event.transform));
    svg.call(zoom).on("dblclick.zoom", null);
    g.append("g").attr("class", "links");
    g.append("g").attr("class", "roles");
    g.append("g").attr("class", "nodes");
    const root = d3.hierarchy(data, (d) => d.children);
    let id = 0;
    root.each((d) => {
        d.id = id++;
        d.size = nodeSize(d);
    });
    Object.assign(ast, { svg, g, zoom, root });
    if (id > 140) collapseExpressions(false);
    update(root, true);
    fit(false);
}

function linkPath(s, t) {
    const sy = s.y + s.size.h - 12;
    const ty = t.y - 12;
    const my = (sy + ty) / 2;
    return `M${s.x},${sy}C${s.x},${my} ${t.x},${my} ${t.x},${ty}`;
}

function update(source, initial) {
    const duration = 300;
    const animate = (selection) => (initial ? selection : selection.transition().duration(duration));
    d3.tree().nodeSize([1, 74]).separation((a, b) => (a.size.w + b.size.w) / 2 + 14)(ast.root);
    const nodes = ast.root.descendants();
    const links = ast.root.links();
    const origin = { x: source.x0 ?? source.x, y: source.y0 ?? source.y, size: source.size };

    const link = ast.g.select(".links").selectAll("path.link").data(links, (d) => d.target.id);
    animate(link.enter().append("path").attr("class", "link")
        .attr("d", () => linkPath(origin, origin))
        .merge(link))
        .attr("d", (d) => linkPath(d.source, d.target));
    animate(link.exit()).attr("d", () => linkPath(source, source)).remove();

    const roleLinks = links.filter((l) => !HIDDEN_ROLES.has(l.target.data.role));
    const role = ast.g.select(".roles").selectAll("text.role").data(roleLinks, (d) => d.target.id);
    animate(role.enter().append("text").attr("class", "role").attr("text-anchor", "middle")
        .attr("x", origin.x).attr("y", origin.y)
        .merge(role)
        .text((d) => d.target.data.role))
        .attr("x", (d) => d.target.x)
        .attr("y", (d) => d.target.y - 17);
    role.exit().remove();

    const node = ast.g.select(".nodes").selectAll("g.node").data(nodes, (d) => d.id);
    const enter = node.enter().append("g").attr("class", "node")
        .attr("transform", `translate(${origin.x},${origin.y})`)
        .style("opacity", 0)
        .on("click", (event, d) => toggle(d))
        .on("dblclick", (event, d) => {
            event.stopPropagation();
            goToLine(d.data.line, d.data.column);
        })
        .on("mouseenter", showTooltip)
        .on("mousemove", moveTooltip)
        .on("mouseleave", () => { $("tooltip").hidden = true; });
    enter.append("rect");
    enter.append("text").attr("class", "label").attr("text-anchor", "middle").attr("y", 4);
    enter.append("text").attr("class", "type").attr("text-anchor", "middle").attr("y", 18);
    enter.append("circle").attr("class", "count-bg").attr("r", 9);
    enter.append("text").attr("class", "count").attr("text-anchor", "middle");

    const all = enter.merge(node);
    all.classed("collapsed", (d) => !!d._children);
    all.select("rect")
        .attr("x", (d) => -d.size.w / 2).attr("y", -12)
        .attr("width", (d) => d.size.w).attr("height", (d) => d.size.h)
        .attr("fill", (d) => d3.color(colorOf(d)).copy({ opacity: 0.13 }))
        .attr("stroke", colorOf);
    all.select("text.label").text((d) => d.data.label);
    all.select("text.type").text((d) => (d.data.type ? ": " + d.data.type : ""));
    all.select("circle.count-bg")
        .attr("cx", (d) => d.size.w / 2).attr("cy", -12)
        .attr("fill", colorOf)
        .style("display", (d) => (d._children ? null : "none"));
    all.select("text.count")
        .attr("x", (d) => d.size.w / 2).attr("y", -8.5)
        .text((d) => (d._children ? "+" + hiddenCount(d) : ""));
    animate(all)
        .attr("transform", (d) => `translate(${d.x},${d.y})`)
        .style("opacity", 1);
    animate(node.exit())
        .attr("transform", `translate(${source.x},${source.y})`)
        .style("opacity", 0).remove();

    nodes.forEach((d) => {
        d.x0 = d.x;
        d.y0 = d.y;
    });
}

function toggle(d) {
    if (d.children) {
        d._children = d.children;
        d.children = null;
    } else if (d._children) {
        d.children = d._children;
        d._children = null;
    } else {
        return;
    }
    update(d, false);
}

function expandAll() {
    if (!ast.root) return;
    ast.root.each((d) => {
        if (d._children) {
            d.children = d._children;
            d._children = null;
        }
    });
    update(ast.root, false);
    setTimeout(() => fit(true), 320);
}

function collapseExpressions(redraw) {
    if (!ast.root) return;
    ast.root.each((d) => {
        const isExpr = d.data.category === "expr" || d.data.category === "literal";
        const parentExpr = d.parent && (d.parent.data.category === "expr");
        if (isExpr && !parentExpr && d.children) {
            d._children = d.children;
            d.children = null;
        }
    });
    if (redraw) {
        update(ast.root, false);
        setTimeout(() => fit(true), 320);
    }
}

function fit(animated) {
    if (!ast.svg) return;
    const box = ast.g.node().getBBox();
    const el = $("ast");
    const width = el.clientWidth;
    const height = el.clientHeight;
    if (!box.width || !width) return;
    let scale = Math.min(1.3, 0.92 * Math.min(width / box.width, height / box.height));
    let tx = width / 2 - scale * (box.x + box.width / 2);
    let ty = Math.max(20, height / 2 - scale * (box.y + box.height / 2));
    if (scale < 0.55) {
        scale = 0.55;
        tx = width / 2 - scale * ast.root.x;
        ty = 30;
    }
    const transform = d3.zoomIdentity.translate(tx, ty).scale(scale);
    (animated ? ast.svg.transition().duration(350) : ast.svg).call(ast.zoom.transform, transform);
}

function showTooltip(event, d) {
    const tip = $("tooltip");
    const lines = [
        `<b>${escapeHtml(d.data.kind)}</b> ${escapeHtml(d.data.label === d.data.kind ? "" : d.data.label)}`,
        d.data.type ? `тип: ${escapeHtml(d.data.type)}` : "",
        d.data.role && d.data.role !== "root" ? `роль: ${escapeHtml(d.data.role)}` : "",
        `рядок ${d.data.line}, стовпець ${d.data.column}`,
        `<span style="opacity:.7">клік — згорнути/розгорнути, подвійний клік — показати в коді</span>`,
    ].filter(Boolean);
    tip.innerHTML = lines.join("<br>");
    tip.hidden = false;
    moveTooltip(event);
}

function moveTooltip(event) {
    const tip = $("tooltip");
    const rect = $("tab-ast").getBoundingClientRect();
    tip.style.left = Math.min(event.clientX - rect.left + 14, rect.width - 330) + "px";
    tip.style.top = event.clientY - rect.top + 14 + "px";
}

$("ast-expand").addEventListener("click", expandAll);
$("ast-statements").addEventListener("click", () => collapseExpressions(true));
$("ast-fit").addEventListener("click", () => fit(true));

renderLegend();
updateGutter();
loadExamples().catch((error) => {
    $("diagnostics").textContent = "Не вдалося завантажити приклади: " + error.message;
});
