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
                                <Chip label={`Yeni: ${summary.created}`}/>
                                <Chip label={`Güncellenen: ${summary.updated}`}/>
                                <Chip label={`Atlanan: ${summary.skipped}`}/>
                                <Chip label={`Yeni müfredat: ${summary.curriculaCreated}`}/>
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
                                {students.content.map(s => (
                                    <TableRow key={s.id} hover>
                                        <TableCell>{s.studentNo}</TableCell>
                                        <TableCell>{s.firstName}</TableCell>
                                        <TableCell>{s.lastName}</TableCell>
                                        <TableCell>{s.program}</TableCell>
                                        <TableCell>{s.entryYear}</TableCell>
                                        <TableCell>{s.classLevel}</TableCell>
                                        <TableCell>{s.curriculum ? `${s.curriculum.catalogName} ${s.curriculum.version}` : ""}</TableCell>
                                        <TableCell>{s.academicStatus}</TableCell>
                                    </TableRow>
                                ))}
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
