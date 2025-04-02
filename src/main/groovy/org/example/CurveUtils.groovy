package org.example

import com.niku.xmlserver.blob.NkCurve
import com.niku.xmlserver.blob.NkSegment
import com.niku.xmlserver.core.NkTime
import de.itdesign.clarity.logging.CommonLogger
import groovy.sql.GroovyRowResult
import groovy.time.TimeCategory

import javax.sql.rowset.serial.SerialBlob
import java.sql.Blob

class CurveUtils {

    CommonLogger cmnLog

    NkCurve getCurveFromBlob(GroovyRowResult rowResult, String curveName) {
        Blob curveBlob_SoftCurve = rowResult?.get(curveName) ? new SerialBlob(rowResult?.get(curveName) as byte[]) : null
        NkCurve Curve = curveBlob_SoftCurve ? new NkCurve(curveBlob_SoftCurve.binaryStream.bytes) : new NkCurve(1)
        return Curve
    }

    NkCurve removeFilterSegments(NkCurve curve, Date start, Integer periods) {
        NkCurve filterCurve = getFilterSegments(curve.clone() as NkCurve, start, periods)
        curve.segments.subSegments(filterCurve.segments)
        cmnLog.debug "removeFilterSegments: " + curve
        return curve
    }

    NkCurve getFilterSegments(NkCurve curve, Date start, Integer periods) {
        NkCurve filterCurve = new NkCurve(1)
        if (curve.sum > 0 && curve.segments.size > 0) {
            use(TimeCategory) {
                for (int i = 0; i < periods; i++) {
                    def startDate = start + i.month
                    def finishDate = start + i.month + 1.month
                    def segExists = 0
                    curve.segments.each { NkSegment segment ->
                        if (segment.startDate >= startDate && segment.finishDate <= finishDate) {
                            segExists = 1
                            filterCurve.segments.setSegment(segment)
                        }
                    }
                    if (segExists == 0) {
                        filterCurve.segments.setSegment(NkTime.toNkTime(startDate), NkTime.toNkTime(finishDate), 0.0D, null)
                    }
                }
            }
        }
        cmnLog.debug("filterCurve: " + filterCurve)
        filterCurve
    }

    NkCurve cloneOptimizedSegments(byte[] bytes) {
        /* grouped segments are split on a monthly basis */
        NkCurve clonedCurve = new NkCurve(1)
        if (bytes) {
            NkCurve curve = new NkCurve(bytes)
            curve.segments.each { NkSegment segment ->
                def start = DateUtil.ensureMidnightStart(segment.startDate)
                def finish = DateUtil.ensureMidnightFinish(segment.finishDate)
                def startDate = start.toLocalDate()
                def endDate = finish.toLocalDate()
                def firstOfMonth = startDate.withDayOfMonth(1)
                def lastOfMonth = startDate.withDayOfMonth(startDate.lengthOfMonth()).plusDays(1)
                cmnLog.debug("segment.startDate: ${segment.startDate} segment.finishDate: ${segment.finishDate} start: ${start} finish: ${finish} firstOfMonth: ${firstOfMonth} lastOfMonth: ${lastOfMonth}")
                while (firstOfMonth.isBefore(endDate)) {
                    /* If multiple segments are grouped, split the segments by month */
                    NkTime startSegment = NkTime.toNkTime((firstOfMonth.isBefore(startDate) ? startDate : firstOfMonth).toDate())
                    NkTime finishSegment = NkTime.toNkTime((endDate.isBefore(lastOfMonth) ? endDate : lastOfMonth).toDate())
                    def rate = segment.rate
                    cmnLog.debug "segment start: $startSegment segment finish: $finishSegment rate: $rate"
                    if (rate > 0.0D) {
                        clonedCurve.segments.setSegment(startSegment, finishSegment, rate, null)
                    }
                    firstOfMonth = firstOfMonth.plus(1, ChronoUnit.MONTHS)
                    lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth()).plusDays(1)
                }
            }
        }
        cmnLog.debug("cloneOptimizedSegments: " + clonedCurve)
        clonedCurve
    }
}
