# Nguồn tài nguyên đồ hoạ

## `app/src/main/assets/items/`

Icon vật phẩm 32×32 lấy từ **Pixel Art Icon Pack - RPG** của **Cainos**
(<https://cainos.itch.io/pixel-art-icon-pack-rpg>).

Giấy phép của pack cho dùng trong dự án miễn phí lẫn thương mại, được sửa đổi,
không bắt buộc ghi công, **nhưng cấm phát tán lại hoặc bán lại**. Vì repo này
công khai, chỉ 9 icon thực sự dùng đến được chép vào đây thay vì cả pack 107
file — thư mục `pixel-art/` chứa bản tải đầy đủ **không được commit**.

| File | Gốc trong pack |
| :--- | :--- |
| `WOODEN_SWORD.png` | `Weapon & Tool/Wooden Sword.png` |
| `STEEL_SWORD.png` | `Weapon & Tool/Iron Sword.png` |
| `RUNE_BLADE.png` | `Weapon & Tool/Golden Sword.png` |
| `LEATHER_VEST.png` | `Equipment/Leather Armor.png` |
| `CHAIN_MAIL.png` | `Equipment/Iron Armor.png` |
| `MANA_RING.png` | `Ore & Gem/Cut Sapphire.png` |
| `SAGE_AMULET.png` | `Misc/Rune Stone.png` |
| `CARD_STREAK.png` | `Misc/Scroll.png` |
| `CARD_CHAMPION.png` | `Misc/Golden Coin.png` |
| `DRAGON_PLATE.png` | `Equipment/Iron Armor.png`, tô lại vàng kim |

`DRAGON_PLATE.png` là bản đổi màu của Iron Armor: pack không có bộ giáp bậc
Huyền thoại nào, mà dùng lại nguyên Iron Armor thì nó trùng hệt Giáp xích.

## `app/src/main/assets/battle/`

`hero.png`, `monsters.png`, `backdrop.png` có sẵn trong dự án từ trước, **chưa
xác minh được nguồn gốc và giấy phép** — cần làm rõ trước khi công bố.

`hero_*.png` (6 file) là bản đổi màu của `hero.png`: chỉ 9 màu trang phục
(5 màu thân, 4 màu áo choàng) bị thay hue trong khi giữ nguyên độ sáng, nên mọi
bước đổ bóng và toàn bộ hình học khung hình giữ nguyên. Da, tóc, da thuộc và
kim loại không đổi. Lưỡi kiếm dùng chung dải sáng với thân áo nên cũng đổi màu
theo — có chủ ý, để cả bộ đồng tông.

Icon của skin trong cửa hàng (`SKIN_*.png` trong `items/`) là khung IDLE đầu
tiên cắt ra từ chính sheet tương ứng, nên thứ bày bán đúng là thứ người chơi sẽ
nhận.
