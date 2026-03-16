

def allY(x,y):
    ty = y - 1
    line = ""
    while ty >= 0:
        line += '[' + str(x) + ',' + str(ty) + '] '
        ty -= 1

    ty = y + 1
    while ty < 9:
        line += '[' + str(x) + ',' + str(ty) + '] '
        ty += 1
    return line

def allX(x,y):
    line = ''
    tx = x - 1
    while tx >= 2:
        line += '[' + str(tx) + ',' + str(y) + '] '
        tx -= 1

    tx = x + 1
    while tx < 11:
        line += '[' + str(tx) + ',' + str(y) + '] '
        tx += 1
    return line

def allXY(x,y):
    line = ""
    if (x%2 != 0 and y%2!=0) or (x%2 == 0 and y%2 == 0):
        tx = x - 1
        ty = y - 1
        while tx >= 2 and ty >= 0:
            line += '[' + str(tx) + ',' + str(ty) + '] '
            tx -= 1
            ty -= 1

        tx = x + 1
        ty = y + 1
        while tx < 11 and ty < 9:
            line += '[' + str(tx) + ',' + str(ty) + '] '
            tx += 1
            ty += 1
    return line

def allYX(x,y):
    line = ''
    if (x % 2 != 0 and y % 2 != 0) or (x % 2 == 0 and y % 2 == 0):
        tx = x - 1
        ty = y + 1
        while tx >= 2 and ty < 9:
            line += '[' + str(tx) + ',' + str(ty) + '] '
            tx -= 1
            ty += 1

        tx = x + 1
        ty = y - 1
        while tx < 11 and ty >= 0:
            line += '[' + str(tx) + ',' + str(ty) + '] '
            tx += 1
            ty -= 1
    return line




x = 2
y = 0

while x < 11:
    y = 0
    while y < 9:
        line = allX(x,y)
        if len(line) > 0:
            print('[' + str(x) + ',' + str(y) + ']-1:' + line)

        line = allY(x,y)
        if len(line) > 0:
            print('[' + str(x) + ',' + str(y) + ']-2:' + line)

        line = allXY(x,y)
        if len(line) > 0:
            print('[' + str(x) + ',' + str(y) + ']-3:' + line)

        line = allYX(x,y)
        if len(line) > 0:
            print('[' + str(x) + ',' + str(y) + ']-4:' + line)
        y+=1
    x+=1



