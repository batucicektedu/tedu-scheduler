import React, { useImperativeHandle, useMemo, useRef, useState, forwardRef } from "react";
import {
    Button, CircularProgress, FormControl, InputLabel, MenuItem, Paper, Select, Table, TableBody,
    TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import {
    distinctFaculties, distinctKinds, facultyLabel, filterPlans, formatNumber, hasNoItems, kindLabel,
    listErrorText,
} from "./curriculaUtils";

// status: "loading" | "error" | "ok"
const CurriculaList = forwardRef(({ plans, status, onRetry, selectedId, onSelect }, ref) => {
    const [faculty, setFaculty] = useState("")
    const [kind, setKind] = useState("")
    const [q, setQ] = useState("")
    const buttons = useRef({})

    useImperativeHandle(ref, () => ({
        focusRow: (id) => { const b = buttons.current[id]; if (b) b.focus() },
    }))

    const faculties = useMemo(() => distinctFaculties(plans), [plans])
    const kinds = useMemo(() => distinctKinds(plans), [plans])
    const shown = useMemo(() => filterPlans(plans, { faculty, kind, q }), [plans, faculty, kind, q])
    const filtering = !!(faculty || kind || q)
    const clear = () => { setFaculty(""); setKind(""); setQ("") }

    return (
        <Paper className="section-card">
            <Typography variant="h6" component="h2">Müfredat Listesi</Typography>
            <div className="filters">
                <FormControl variant="outlined" size="small" className="filter-select">
                    <InputLabel id="curricula-faculty-label">Fakülte</InputLabel>
                    <Select labelId="curricula-faculty-label" label="Fakülte" value={faculty}
                            onChange={e => setFaculty(e.target.value)}>
                        <MenuItem value="">Tümü</MenuItem>
                        {faculties.map(f => <MenuItem key={f} value={f}>{facultyLabel(f)}</MenuItem>)}
                    </Select>
                </FormControl>
                <FormControl variant="outlined" size="small" className="filter-select">
                    <InputLabel id="curricula-kind-label">Tür</InputLabel>
                    <Select labelId="curricula-kind-label" label="Tür" value={kind}
                            onChange={e => setKind(e.target.value)}>
                        <MenuItem value="">Tümü</MenuItem>
                        {kinds.map(k => <MenuItem key={k} value={k}>{kindLabel(k)}</MenuItem>)}
                    </Select>
                </FormControl>
                <TextField label="Ara (program, kod, müfredat)" size="small" variant="outlined" value={q}
                           className="filter-search" onChange={e => setQ(e.target.value)}/>
                <Button size="small" onClick={clear} disabled={!filtering}>Süzgeci temizle</Button>
                {status === "ok" && plans.length > 0 && (
                    <Typography variant="body2" className="muted" role="status">
                        {`${shown.length} / ${plans.length} müfredat`}
                    </Typography>
                )}
            </div>

            {status === "loading" && (
                <div className="center-box"><CircularProgress aria-label="Müfredatlar yükleniyor"/></div>
            )}
            {status === "error" && (
                <Alert severity="error" role="alert"
                       action={<Button color="inherit" size="small" onClick={onRetry}>Yeniden dene</Button>}>
                    {listErrorText()}
                </Alert>
            )}
            {status === "ok" && plans.length === 0 && (
                <Typography className="empty-text">Henüz müfredat yok. Yukarıdan Excel dosyalarını içe aktarın.</Typography>
            )}
            {status === "ok" && plans.length > 0 && shown.length === 0 && (
                <div className="empty-text">
                    <Typography component="span">Süzgece uyan müfredat yok. </Typography>
                    <Button size="small" onClick={clear}>Süzgeci temizle</Button>
                </div>
            )}
            {status === "ok" && shown.length > 0 && (
                <TableContainer className="list-box">
                    <Table size="small" stickyHeader>
                        <TableHead>
                            <TableRow>
                                <TableCell>Program</TableCell>
                                <TableCell className="hide-xs">Tür</TableCell>
                                <TableCell className="hide-xs">Müfredat</TableCell>
                                <TableCell className="hide-xs">Fakülte</TableCell>
                                <TableCell className="hide-xs" align="right">Ders</TableCell>
                                <TableCell className="hide-xs" align="right">Seçmeli</TableCell>
                                <TableCell className="hide-xs" align="right">Kredi</TableCell>
                                <TableCell align="right">AKTS</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {shown.map(p => {
                                const empty = hasNoItems(p)
                                const num = (v) => (empty ? "—" : formatNumber(v))
                                const selected = p.id === selectedId
                                return (
                                    <TableRow key={p.id} hover selected={selected} aria-selected={selected}
                                              className="clickable-row" onClick={() => onSelect(p.id)}>
                                        <TableCell>
                                            <button type="button" className="link-button"
                                                    ref={el => { if (el) buttons.current[p.id] = el; else delete buttons.current[p.id] }}
                                                    onClick={e => { e.stopPropagation(); onSelect(p.id) }}>
                                                {p.programName || p.programCode}
                                            </button>
                                            {p.programCode && p.programCode !== p.programName && (
                                                <div className="muted small">{p.programCode}</div>
                                            )}
                                            <div className="only-xs muted small">
                                                {[kindLabel(p.kind), p.label, facultyLabel(p.faculty)].join(" · ")}
                                            </div>
                                        </TableCell>
                                        <TableCell className="hide-xs">{kindLabel(p.kind)}</TableCell>
                                        <TableCell className="hide-xs">{p.label}</TableCell>
                                        <TableCell className="hide-xs">{facultyLabel(p.faculty)}</TableCell>
                                        <TableCell className="hide-xs" align="right">{empty ? "—" : p.courseCount}</TableCell>
                                        <TableCell className="hide-xs" align="right">{empty ? "—" : p.electiveCount}</TableCell>
                                        <TableCell className="hide-xs" align="right">{num(p.totalCredits)}</TableCell>
                                        <TableCell align="right">{num(p.totalEcts)}</TableCell>
                                    </TableRow>
                                )
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
            )}
        </Paper>
    )
})

export default CurriculaList
