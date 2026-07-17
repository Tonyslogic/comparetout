# Third-party notices

Eco Power Optimiser is licensed under the Apache License, Version 2.0 (see
[LICENSE](LICENSE)). This file collects notices for third-party projects that
informed parts of this codebase but whose code is **not** included in it.

## SEMOpx market data (data source, not redistributed)

The dynamic-tariff feature fetches historical I-SEM day-ahead auction results
**on the user's own device** from SEMOpx's public publications
(`reports.semopx.com` static reports and the general-publications historical
files on `www.semopx.com`). That information is published by SEMOpx (EirGrid
plc and SONI Limited) on an "AS IS" basis for general information purposes.

No SEMOpx data is included in this application or its distributions, and no
SEMOpx-derived prices are exported or shared by the app (dynamic plans export
their supplier *terms* only; each installation materialises prices locally).
Reproduction of SEMOpx publications requires the prior written permission of
EirGrid plc and SONI Limited. Generated plans carry an attribution and the
AS-IS caveat in their reference notes.

## alphaess-openAPI (MIT)

The AlphaESS bind-SN flow (`POST /api/getVerificationCode` + `POST /api/bindSn`
in `app/src/main/java/com/tfcode/comparetout/importers/alphaess/OpenAlphaESSClient.java`)
was written against the endpoint contract documented by the community Python
client [CharlesGillanders/alphaess-openAPI](https://github.com/CharlesGillanders/alphaess-openAPI)
and its Postman collection, distributed under the following license:

```
MIT License

Copyright (c) 2021 Charles Gillanders

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## soliscloud_api (MIT)

The SolisCloud request-signing procedure and endpoint contract implemented in
`app/src/main/java/com/tfcode/comparetout/importers/solis/SolisCloudClient.java`
were written against the SolisCloud Platform API Document V2.0 and verified
against the community Python client
[hultenvp/soliscloud_api](https://github.com/hultenvp/soliscloud_api), which is
distributed under the following license:

```
MIT License

Copyright (c) 2023 hultenvp

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
