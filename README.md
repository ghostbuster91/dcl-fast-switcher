# DCL-FAST-SWITCHER

Stands for docker-compose logs fast switcher

## Installation
Using [ktm](https://github.com/ghostbuster91/ktm)

`$ ktm install com.github.ghostbuster91:dcl-fast-switcher --version 0.1`


## Usage

1. Start some docker compose `docker-compose up`

2. Within the same directory start fast switcher `dcl-fast-switcher docker-config.yml`
    - you can omit the parameter if your docker-config is named as `docker-compose.yml`

3. At the begging a key to service mapping will be presented:

    ```
    Services mapping:
    0 -> some service
    1 -> other service
    2 -> one more service
    3 -> and so on...
    ```
4. To switch between services just press relevant key

5. Additional you can concat streams using alt key, and split them using shift key