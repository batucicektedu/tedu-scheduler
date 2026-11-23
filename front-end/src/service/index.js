import axios from "axios"
import * as config from "../config"
import * as TimetableService from "./TimetableService"
import * as SemesterService from "./SemesterService"
import * as CourseService from "./CourseService"
import * as StudentService from "./StudentService"
import * as CoursePlanService from "./CoursePlanService"

const api = axios.create({
    baseURL: config.default.BACKEND_URL,
    headers: {
        'Access-Control-Allow-Origin': '*',
        'Content-Type': 'application/json',
    }
})

export default api

export { TimetableService, SemesterService, CourseService, StudentService, CoursePlanService }