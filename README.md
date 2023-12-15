# print-server

This spring application provides a web interface for multi-user printing based on CUPS.

## Features

- balance per user
- various print options
- price per page/two-sided page and printer
- support multiple Printers
- job history

## Requirements

- Java 17
- MariaDB server
- CUPS

## Setup

For faster print updates, change the page format line in `cupsd.conf` to:
```
PageLogFormat "%p,%j,%T,%P,%C,%{job-name},%{sides},%{job-impressions-completed},%{job-media-sheets-completed}"
```

## Code Style

For java code, we use the Google java style guide, which can be found here:
https://google.github.io/styleguide/javaguide.html

## License

- The source is licensed under the GNU GPLv2 license that can be found in the [LICENSE](LICENSE)
  file.