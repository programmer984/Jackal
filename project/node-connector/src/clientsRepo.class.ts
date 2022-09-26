import { clientInfo } from "./clientInfo.class";

export class clientsRepo {

    repo = new Map<string, clientInfo>()

    validateAndAdd = (clientInfo: clientInfo) => {
        if (clientInfo.Name.length > 0) {
            this.repo.set(clientInfo.Name, clientInfo)
        }
    }

    getByName = (name: string, minAge: number): clientInfo | null => {
        let info = this.repo.get(name) || null
        if (info!=null && info.CreationTimestamp > minAge){
            return info
        }
        return null
    }
}