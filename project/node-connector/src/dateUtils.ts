
export class DateUtils {
    
    public static currentTimestamp() {
        return Math.floor(new Date().getTime() / 1000)
    }
}