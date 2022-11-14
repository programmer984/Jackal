import express from 'express'
import { Request, Response } from "express";
import { clientInfo } from './clientInfo.class';
import { clientsRepo } from './clientsRepo.class'
import { DateUtils } from './dateUtils'

const repo = new clientsRepo()

const app = express()
app.use(express.json())
const port = process.env.PORT ?? 3000

app.listen(port, () => {
    console.log(`application started ${port}`)
})

const addClientInfo = async (req: Request, res: Response) => {
    try {
        const info = clientInfo.deserialize(req.body)
        repo.validateAndAdd(info)
        res.status(200)
    } catch (e: any) {
        res.status(500)
        res.send(e.message)
    } finally {
        res.end()
    }
}


const getClientInfo = async (req: Request, res: Response) => {

    try {
        let clientName = req.query["clientName"] as string
        let oldness = Number(req.query["oldness"])
        const info = repo.getByName(clientName, oldness)
        res.setHeader("Content-Type", "application/json")
        res.send(info)
        res.status(200)
    } catch (e: any) {
        res.status(500)
        res.send(e.message)
    } finally {
        res.end()
    }
}
const ping = async (req: Request, res: Response) => {
    try {
        res.setHeader("Content-Type", "text/plain")
        res.status(200)
        const now = DateUtils.currentTimestamp()
        res.send("timestamp " + now)
    } catch (e: any) {
        res.status(500)
        res.send(e.message)
    } finally {
        res.end()
    }
}

app.post("/clientInfo", addClientInfo);
app.get("/clientInfo", getClientInfo);
app.get("/", ping);


/*
let count = 1
setInterval(() =>{
    console.log(count++)

},1000)*/