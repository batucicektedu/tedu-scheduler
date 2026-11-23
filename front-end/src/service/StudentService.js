import api from "./index";

export const importStudents = (key, file) => {
    const form = new FormData()
    form.append("key", key)
    form.append("file", file)
    return api.post("/students/import", form, {
        headers: { "Content-Type": "multipart/form-data" },
    })
}

export const getStudents = ({ page = 0, size = 25, program, entryYear, curriculumId, q } = {}) => {
    const params = { page, size }
    if (program) params.program = program
    if (entryYear) params.entryYear = entryYear
    if (curriculumId) params.curriculumId = curriculumId
    if (q) params.q = q
    return api.get("/students", { params })
}

export const getStudentFilters = () => {
    return api.get("/students/filters")
}

export const getCurricula = () => {
    return api.get("/curricula")
}
