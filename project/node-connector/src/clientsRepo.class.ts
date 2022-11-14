import { clientInfo } from "./clientInfo.class";
import { DateUtils } from "./dateUtils";

export class clientsRepo {

    repo = new Map<string, clientInfo>()

    validateAndAdd = (clientInfo: clientInfo) => {
        if (clientInfo.Name.length > 0) {
            this.repo.set(clientInfo.Name, clientInfo)
        }
    }

    getByName = (name: string, oldness: number): clientInfo | null => {
        let info = this.repo.get(name) || null
        const now = DateUtils.currentTimestamp()
        if (info!=null && (now - info.CreationTimestamp) < oldness){
            return info
        }
        return null
    }
}