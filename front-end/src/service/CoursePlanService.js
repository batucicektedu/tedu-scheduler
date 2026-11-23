import api from "./index";

// Müfredat dosyalarını içe aktarır. dryRun=true ise yalnız denetler, hiçbir şey kaydetmez.
export const importCoursePlans = (key, files, dryRun, cancelToken) => {
    const form = new FormData()
    form.append("key", key)
    files.forEach(f => form.append("files", f))
    form.append("dryRun", dryRun ? "true" : "false")
    return api.post("/course-plans/import", form, {
        headers: { "Content-Type": "multipart/form-data" },
        cancelToken,
    })
}

export const getCoursePlans = () => {
    return api.get("/course-plans")
}

export const getCoursePlan = (id) => {
    return api.get(`/course-plans/${id}`)
}
