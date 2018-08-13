import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter
import javax.inject.Inject
import play.filters.gzip.GzipFilter

class Filters @Inject() (
  gzip: GzipFilter,
  csrf: CSRFFilter,
  access: AccessLoggingFilter
) extends DefaultHttpFilters(gzip, csrf, access)
