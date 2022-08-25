# tg-shell-bot - a bot allows you to execute shell commands

The bot allows to execute commands on remote machine it is deployed.

Features:

- you can filter the users who can access the bot
- process output (stderr & stdout) is auto-updated and displayed by bot
- multiple processes can run in parallel
- running processes can be terminated with button (use case: start ngrok, use it, terminate when all's done)

---

Configuration file is expected to present as `$HOME/.config/tg-shell-bot/config.property` and contain:

```properties
telegram.api.key=0000000000:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
telegram.users.allowed=foobar,foobaz
shell.path=/bin/bash
```

You can specify custom config path or override properties with commandline arguments:

```bash
./tg-shell-bot -config "$CONFIG_PATH" -allowedUsers "user0,user1,user2" -shellPath "/bin/sh"
```

---

<img width="652" alt="image" src="https://user-images.githubusercontent.com/36963534/186650170-034f75e2-e471-4bd8-acb7-a925e908b6f5.png">

<img width="663" alt="image" src="https://user-images.githubusercontent.com/36963534/186649759-145bc6ca-5280-4be9-96ad-69d3533c279a.png">

<img width="662" alt="image" src="https://user-images.githubusercontent.com/36963534/186650117-f4a1ea0d-6bcf-49a2-ae49-0c1b4381f04c.png">

