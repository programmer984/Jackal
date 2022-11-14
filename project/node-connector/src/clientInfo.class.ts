import { DateUtils } from "./dateUtils"

export class clientInfo {

  Name: string = "";
  CreationTimestamp: number = 0;
  LocalIP: string[] = [];
  LocalPort: number = 0;
  PublicIP: string = "";
  PublicPort: number = 0;
  PublicKey: string = "";

  getUniqueId() {
    return this.Name;
  }

  static deserialize(input: any): clientInfo {
    let result = new clientInfo()
    result.Name = input.Name;
    result.CreationTimestamp = DateUtils.currentTimestamp()
    result.LocalIP = input.LocalIP
    result.LocalPort = input.LocalPort
    result.PublicIP = input.PublicIP
    result.PublicPort = input.PublicPort
    result.PublicKey = input.PublicKey
    return result
  }

}