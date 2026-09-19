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
| `OAK_STAFF.png` | `Weapon & Tool/Wooden Staff.png` |
| `MAGIC_WAND.png` | `Weapon & Tool/Magic Wand.png` |
| `EMERALD_STAFF.png` | `Weapon & Tool/Emerald Staff.png` |
| `RUBY_STAFF.png` | `Weapon & Tool/Ruby Staff.png` |
| `HUNTER_BOW.png` | `Weapon & Tool/Bow.png` |
| `WAR_AXE.png` | `Weapon & Tool/Axe.png` |
| `WOODEN_ARMOR.png` | `Equipment/Wooden Armor.png` |
| `LEATHER_HOOD.png` | `Equipment/Leather Helmet.png` |
| `IRON_HELMET.png` | `Equipment/Iron Helmet.png` |
| `WIZARD_HAT.png` | `Equipment/Wizard Hat.png` |
| `SCHOLAR_LANTERN.png` | `Misc/Lantern.png` |
| `CUT_RUBY.png` | `Ore & Gem/Cut Ruby.png` |
| `CUT_EMERALD.png` | `Ore & Gem/Cut Emerald.png` |
| `DIAMOND_CHARM.png` | `Ore & Gem/Diamond.png` |
| `CARD_BOOKWORM.png` | `Misc/Book.png` |
| `CARD_EXPLORER.png` | `Misc/Map.png` |
| `CARD_TREASURE.png` | `Misc/Chest.png` |

`DRAGON_PLATE.png` là bản đổi màu của Iron Armor: pack không có bộ giáp bậc
Huyền thoại nào, mà dùng lại nguyên Iron Armor thì nó trùng hệt Giáp xích.

27 icon trên là toàn bộ phần đang dùng của pack 107 file. Vẫn giữ đúng nguyên
tắc chỉ chép cái nào thực sự có vật phẩm tương ứng trong `catalog.py`, và
`pixel-art/` vẫn **không được commit**.

## `app/src/main/assets/chests/`

3 rương 32×32 của thanh năng động (màn Nhiệm vụ ngày và popup mở rương) lấy
từ **12 Pixel Chest Sprites** của **PixelExplosive**
(<https://pixelexplosive.itch.io>, Twitter @PixelExplosive).

Giấy phép cho dùng trong dự án cá nhân lẫn thương mại, được sửa đổi, không bắt
buộc ghi công, **nhưng cấm phát tán lại hoặc bán lại** (kể cả bản đã sửa) và cấm
dùng cho NFT hay dữ liệu huấn luyện AI. Như với pack icon ở trên, chỉ 3 sprite
thực sự dùng được chép vào repo, không chép cả pack 12 file.

| File | Gốc trong pack |
| :--- | :--- |
| `bronze.png` | `chest_01.png` |
| `silver.png` | `chest_02.png` |
| `gold.png` | `chest_11.png` |

## `app/src/main/assets/battle/`

Nguồn và giấy phép của toàn bộ thư mục này nay ghi ở
`tools/battle-art/ATTRIBUTION.md` — gồm `hero.png` (Chiến binh, cắt lại từ dự
án Unity `TurnBasedBattle` bên cạnh), `hero_mage.png` (Wizard Pack của LuizMelo,
CC0) và `hero_archer.png` (Huntress 2 của LuizMelo, CC0), cùng monsters và
backdrop. Tất cả do `build_battle_art.py` sinh ra.

`hero_rookie/scholar/office/night/orator/laureate.png` (6 file) là bản đổi màu
của `hero.png`: chỉ 9 màu trang phục (5 màu thân, 4 màu áo choàng) bị thay hue
trong khi giữ nguyên độ sáng, nên mọi bước đổ bóng và toàn bộ hình học khung
hình giữ nguyên. Da, tóc, da thuộc và kim loại không đổi. Lưỡi kiếm dùng chung
dải sáng với thân áo nên cũng đổi màu theo — có chủ ý, để cả bộ đồng tông.

`hero_mage_*.png` và `hero_archer_*.png` (6 file) do
`tools/battle-art/build_class_skins.py` sinh ra theo cùng nguyên tắc, nhưng
khoanh vùng trang phục bằng **dải hue** thay vì liệt kê từng màu: áo pháp sư
nằm ở hue 235–305, áo xạ thủ ở 80–140, còn da, da thuộc, gỗ và kim loại đều ở
10–45 nên không bị đụng tới. Riêng bộ `dusk` của Xạ thủ được nhân bão hoà lên
1,9 lần: áo gốc bão hoà thấp, chỉ xoay hue thì ra nâu đỏ trùng với chính da
thuộc của nhân vật.

Một bộ trang phục chỉ dùng được cho trường phái sinh ra nó. Mặc nhầm bộ của
trường phái khác thì nhân vật giữ nguyên hình — xem `BattleArt.COSTUMES`.

Icon của skin trong cửa hàng (`SKIN_*.png` trong `items/`) là khung IDLE đầu
tiên cắt ra từ chính sheet tương ứng, nên thứ bày bán đúng là thứ người chơi sẽ
nhận.
