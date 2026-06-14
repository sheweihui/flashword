#!/usr/bin/env python3
"""
背单词助手 Agent - 命令行入口

通过后端 API 进行单词查询、签到、秒杀等操作。

用法:
    python main.py login <username> <password>
    python main.py search <keyword>
    python main.py checkin
    python main.py points
    python main.py flash-sales
    python main.py books
    python main.py ai-fill <word>
    python main.py interactive    # 交互式模式
"""

import sys
from loguru import logger

from config.settings import LOG_LEVEL, LOG_FILE
from api.client import ApiClient
from agent.core import Agent


def setup_logger():
    logger.remove()
    logger.add(sys.stderr, level=LOG_LEVEL, format="<level>{level:7}</level> | {message}")
    logger.add(LOG_FILE, rotation="10 MB", level="DEBUG")


def print_usage():
    print(__doc__)


def main():
    setup_logger()

    client = ApiClient()
    agent = Agent(client)

    if len(sys.argv) < 2:
        print_usage()
        return

    command = sys.argv[1]

    try:
        if command == "login":
            if len(sys.argv) < 4:
                print("用法: python main.py login <username> <password>")
                return
            print(agent.login(sys.argv[2], sys.argv[3]))

        elif command == "register":
            if len(sys.argv) < 4:
                print("用法: python main.py register <username> <password> [nickname]")
                return
            nickname = sys.argv[4] if len(sys.argv) > 4 else ""
            print(agent.register(sys.argv[2], sys.argv[3], nickname))

        elif command == "search":
            if len(sys.argv) < 3:
                print("用法: python main.py search <keyword>")
                return
            print(agent.run("search_word", keyword=sys.argv[2]))

        elif command == "checkin":
            print(agent.run("daily_checkin"))

        elif command == "points":
            print(agent.run("get_points_balance"))

        elif command == "flash-sales" or command == "flash":
            print(agent.run("get_flash_sale_list"))

        elif command == "books" or command == "store":
            print(agent.run("get_store_books"))

        elif command == "my-books":
            print(agent.run("get_user_books"))

        elif command == "ai-fill":
            if len(sys.argv) < 3:
                print("用法: python main.py ai-fill <word>")
                return
            print(agent.run("ai_fill_word", word_text=sys.argv[2]))

        elif command == "interactive":
            interactive_mode(agent)

        elif command in ("-h", "--help"):
            print_usage()

        else:
            print(f"未知命令: {command}")
            print_usage()

    except Exception as e:
        logger.error(f"执行失败: {e}")
        sys.exit(1)


def interactive_mode(agent: Agent):
    """交互式命令行模式"""
    print("=" * 50)
    print("  背单词助手 Agent - 交互式模式")
    print("  输入 help 查看命令列表，exit 退出")
    print("=" * 50)

    while True:
        try:
            cmd = input("\n>> ").strip()
            if not cmd:
                continue
            if cmd in ("exit", "quit", "q"):
                print("再见！")
                break
            if cmd == "help":
                print("""
可用命令:
  login <user> <pass>     登录
  search <word>           查单词
  checkin                 每日签到
  points                  查看积分
  flash                   秒杀列表
  books                   商店单词书
  my-books                我的单词本
  ai-fill <word>          AI 补全单词
  exit                    退出
""")
                continue

            parts = cmd.split()
            main_cmd = parts[0]

            if main_cmd == "login" and len(parts) >= 3:
                print(agent.login(parts[1], parts[2]))
            elif main_cmd == "search" and len(parts) >= 2:
                print(agent.run("search_word", keyword=parts[1]))
            elif main_cmd == "checkin":
                print(agent.run("daily_checkin"))
            elif main_cmd == "points":
                print(agent.run("get_points_balance"))
            elif main_cmd == "flash":
                print(agent.run("get_flash_sale_list"))
            elif main_cmd == "books":
                print(agent.run("get_store_books"))
            elif main_cmd == "my-books":
                print(agent.run("get_user_books"))
            elif main_cmd == "ai-fill" and len(parts) >= 2:
                print(agent.run("ai_fill_word", word_text=parts[1]))
            else:
                print("未知命令，输入 help 查看帮助")

        except KeyboardInterrupt:
            print("\n再见！")
            break
        except Exception as e:
            logger.error(f"执行错误: {e}")


if __name__ == "__main__":
    main()
