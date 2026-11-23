import React, { useCallback, useEffect, useState } from "react";
import {
    Button, Chip, CircularProgress, FormControl, InputLabel, MenuItem, Paper, Select,
    Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
    TextField, Typography,
} from "@material-ui/core";
import { Alert } from "@material-ui/lab";
import cogoToast from "cogo-toast";
import { StudentService } from "../../service";
import "./StudentsPage.scss";

const errorText = (err) => {
    const res = err.response
    if (!res) return "Sunucuya ulaşılamadı"
    if (res.status === 403) return "Anahtar hatalı"
    if (typeof res.data === "string" && res.data) return res.data
    if (res.data && res.data.message) return res.data.message
    return "Hata: " + res.status
}

const TYPE_LABELS = { DOUBLE_MAJOR: "ÇAP", MINOR: "Yandal", OTHER: "Diğer" }

const curriculumLabel = (c) => (c ? `${c.catalogName || ""} ${c.version || ""}`.trim() : "")

// Programme records of a student, falling back to the student's own (primary programme) fields.
const programsOf = (s) => {
    if (s.programs && s.programs.length > 0) return s.programs
    return [{
        id: null, program: s.program, programType: "MAIN", classLevel: s.classLevel,
        curriculum: s.curriculum, academicStatus: s.academicStatus,
    }]
}

const cellValue = (v, multi) => (v || (multi ? "—" : ""))

const StudentsPage = () => {
    const [key, setKey] = useState("")
    const [file, setFile] = useState(null)
    const [importing, setImporting] = useState(false)
    const [summary, setSummary] = useState(null)
    const [importError, setImportError] = useState("")

    const [filters, setFilters] = useState({ programs: [], entryYears: [] })
    const [curricula, setCurricula] = useState([])
    const [students, setStudents] = useState({ content: [], totalElements: 0 })
    const [loading, setLoading] = useState(false)

    const [program, setProgram] = useState("")
    const [entryYear, setEntryYear] = useState("")
    const [curriculumId, setCurriculumId] = useState(null)
    const [search, setSearch] = useState("")
    const [q, setQ] = useState("")
    const [page, setPage] = useState(0)
    const [size, setSize] = useState(25)

    useEffect(() => {
        const t = setTimeout(() => { setQ(search); setPage(0) }, 400)
        return () => clearTimeout(t)
    }, [search])

    const loadMeta = useCallback(() => {
        StudentService.getStudentFilters().then(r => setFilters({
            programs: r.data.programs || [], entryYears: r.data.entryYears || [],
        })).catch(() => {})
        StudentService.getCurricula().then(r => setCurricula(r.data || [])).catch(() => {})
    }, [])

    const loadStudents = useCallback(() => {
        setLoading(true)
        StudentService.getStudents({ page, size, program, entryYear, curriculumId, q })
            .then(r => setStudents(r.data))
            .catch(err => cogoToast.error(errorText(err)))
            .finally(() => setLoading(false))
    }, [page, size, program, entryYear, curriculumId, q])

    useEffect(() => { loadMeta() }, [loadMeta])
    useEffect(() => { loadStudents() }, [loadStudents])

    const handleImport = (e) => {
        e.preventDefault()
        if (!key || !file) {
            setImportError("Anahtar ve dosya gerekli")
            return
        }
        setImporting(true)
        setImportError("")
        setSummary(null)
        StudentService.importStudents(key, file)
            .then(r => {
                setSummary(r.data)
                loadMeta()
                if (page === 0) loadStudents(); else setPage(0)
            })
            .catch(err => setImportError(errorText(err)))
            .finally(() => setImporting(false))
    }

    const selectedCurriculum = curricula.find(c => c.id === curriculumId)
    const selectCurriculum = (id) => { setCurriculumId(id === curriculumId ? null : id); setPage(0) }

    return (
        <div className="students-page">
            <div className="students-container">
                <Typography variant="h4">Öğrenci Yönetimi</Typography>

                <Paper className="section-card">
                    <Typography variant="h6">Öğrenci Verisi İçe Aktar</Typography>
                    <form className="import-form" onSubmit={handleImport}>
                        <TextField label="Yönetici Anahtarı" type="password" value={key}
                                   onChange={e => setKey(e.target.value)} size="small" variant="outlined"/>
                        <Button variant="outlined" component="label">
                            {file ? file.name : "Excel Dosyası Seç"}
                            <input type="file" accept=".xlsx,.xls" hidden
                                   onChange={e => setFile(e.target.files[0] || null)}/>
                        </Button>
                        <Button type="submit" variant="contained" color="primary" disabled={importing}
                                startIcon={importing ? <CircularProgress size={16}/> : null}>
                            İçe Aktar
                        </Button>
                    </form>
                    {importError && <Alert severity="error" className="import-summary">{importError}</Alert>}
                    {summary && (
                        <div className="import-summary">
                            <Alert severity="success">İçe aktarma tamamlandı</Alert>
                            <div className="summary-chips">
                                <Chip label={`Toplam satır: ${summary.totalRows}`}/>
                                <Chip label={`Yeni öğrenci: ${summary.created}`}/>
                                <Chip label={`Güncellenen öğrenci: ${summary.updated}`}/>
                                <Chip label={`Yeni program kaydı: ${summary.programsCreated || 0}`}/>
                                <Chip label={`Güncellenen program kaydı: ${summary.programsUpdated || 0}`}/>
                                <Chip label={`Kaldırılan program kaydı: ${summary.programsRemoved || 0}`}/>
                                <Chip label={`Birden çok programlı öğrenci: ${summary.multiProgramStudents || 0}`}/>
                                <Chip label={`Atlanan: ${summary.skipped}`}/>
                                <Chip label={`Yeni müfredat: ${summary.curriculaCreated}`}/>
                                <Chip label={`Müfredatsız program: ${summary.withoutCurriculum || 0}`}/>
                            </div>
                            {summary.errors && summary.errors.length > 0 && (
                                <>
                                    <Typography variant="subtitle2">Hatalar</Typography>
                                    <ul>
                                        {summary.errors.map((er, i) => (
                                            <li key={i}>Satır {er.row}: {er.message}</li>
                                        ))}
                                    </ul>
                                </>
                            )}
                        </div>
                    )}
                </Paper>

                <Paper className="section-card">
                    <Typography variant="h6">Müfredatlar</Typography>
                    <TableContainer>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Program</TableCell>
                                    <TableCell>Fakülte</TableCell>
                                    <TableCell>Katalog</TableCell>
                                    <TableCell>Versiyon</TableCell>
                                    <TableCell align="right">Öğrenci Sayısı</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {curricula.map(c => (
                                    <TableRow key={c.id} hover className="clickable-row"
                                              selected={c.id === curriculumId}
                                              onClick={() => selectCurriculum(c.id)}>
                                        <TableCell>{c.program}</TableCell>
                                        <TableCell>{c.faculty}</TableCell>
                                        <TableCell>{c.catalogCode} {c.catalogName}</TableCell>
                                        <TableCell>{c.version}</TableCell>
                                        <TableCell align="right">{c.studentCount}</TableCell>
                                    </TableRow>
                                ))}
                                {curricula.length === 0 && (
                                    <TableRow><TableCell colSpan={5} align="center">Müfredat yok</TableCell></TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Paper>

                <Paper className="section-card">
                    <Typography variant="h6">Öğrenciler</Typography>
                    <div className="filters">
                        <FormControl variant="outlined" size="small" style={{ minWidth: 200 }}>
                            <InputLabel>Program</InputLabel>
                            <Select value={program} label="Program"
                                    onChange={e => { setProgram(e.target.value); setPage(0) }}>
                                <MenuItem value="">Tümü</MenuItem>
                                {filters.programs.map(p => <MenuItem key={p} value={p}>{p}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <FormControl variant="outlined" size="small" style={{ minWidth: 140 }}>
                            <InputLabel>Giriş Yılı</InputLabel>
                            <Select value={entryYear} label="Giriş Yılı"
                                    onChange={e => { setEntryYear(e.target.value); setPage(0) }}>
                                <MenuItem value="">Tümü</MenuItem>
                                {filters.entryYears.map(y => <MenuItem key={y} value={y}>{y}</MenuItem>)}
                            </Select>
                        </FormControl>
                        <TextField label="Ara (no / ad)" size="small" variant="outlined" value={search}
                                   onChange={e => setSearch(e.target.value)}/>
                        {curriculumId != null && (
                            <Chip color="primary"
                                  label={selectedCurriculum
                                      ? `Müfredat: ${selectedCurriculum.catalogName} ${selectedCurriculum.version}`
                                      : `Müfredat #${curriculumId}`}
                                  onDelete={() => { setCurriculumId(null); setPage(0) }}/>
                        )}
                        {loading && <CircularProgress size={20}/>}
                    </div>
                    <TableContainer>
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Öğrenci No</TableCell>
                                    <TableCell>Ad</TableCell>
                                    <TableCell>Soyad</TableCell>
                                    <TableCell>Program</TableCell>
                                    <TableCell>Giriş Yılı</TableCell>
                                    <TableCell>Sınıf</TableCell>
                                    <TableCell>Müfredat</TableCell>
                                    <TableCell>Akademik Durum</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {students.content.map(s => {
                                    const progs = programsOf(s)
                                    const multi = progs.length > 1
                                    return progs.map((p, i) => (
                                        <TableRow key={`${s.id}-${p.id != null ? p.id : i}`} hover
                                                  className={i === 0 ? "student-first-row" : "student-extra-row"}>
                                            {i === 0 && <TableCell rowSpan={progs.length}>{s.studentNo}</TableCell>}
                                            {i === 0 && <TableCell rowSpan={progs.length}>{s.firstName}</TableCell>}
                                            {i === 0 && <TableCell rowSpan={progs.length}>{s.lastName}</TableCell>}
                                            <TableCell>
                                                {p.program}
                                                {TYPE_LABELS[p.programType] && (
                                                    <Chip size="small" variant="outlined" className="program-chip"
                                                          label={TYPE_LABELS[p.programType]}/>
                                                )}
                                            </TableCell>
                                            {i === 0 && <TableCell rowSpan={progs.length}>{s.entryYear}</TableCell>}
                                            <TableCell>{cellValue(p.classLevel, multi)}</TableCell>
                                            <TableCell>{cellValue(curriculumLabel(p.curriculum), multi)}</TableCell>
                                            <TableCell>{cellValue(p.academicStatus, multi)}</TableCell>
                                        </TableRow>
                                    ))
                                })}
                                {students.content.length === 0 && !loading && (
                                    <TableRow><TableCell colSpan={8} align="center">Kayıt bulunamadı</TableCell></TableRow>
                                )}
                            </TableBody>
                        </Table>
                    </TableContainer>
                    <TablePagination component="div" count={students.totalElements || 0}
                                     page={page} rowsPerPage={size} rowsPerPageOptions={[25, 50, 100]}
                                     labelRowsPerPage="Sayfa başına satır:"
                                     onPageChange={(e, p) => setPage(p)}
                                     onRowsPerPageChange={e => { setSize(parseInt(e.target.value, 10)); setPage(0) }}/>
                </Paper>
            </div>
        </div>
    )
}

export default StudentsPage
