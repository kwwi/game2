

def allY(x,y):
    ty = y - 1
    while ty >= 0:
        print('[' + str(x) + ',' + str(ty) + '] ', end="")
        ty -= 1

    ty = y + 1
    while ty < 9:
        print('[' + str(x) + ',' + str(ty) + '] ', end="")
        ty += 1

def allX(x,y):
    tx = x - 1
    while tx >= 2:
        print('[' + str(tx) + ',' + str(y) + '] ', end="")
        tx -= 1

    tx = x + 1
    while tx < 11:
        print('[' + str(tx) + ',' + str(y) + '] ', end="")
        tx += 1

def allXY(x,y):
    if not((x%2 != 0 and y%2!=0) or (x%2 == 0 and y%2 == 0)):
        return
    tx = x - 1
    ty = y - 1
    while tx >= 2 and ty >= 0:
        print('[' + str(tx) + ',' + str(ty) + '] ', end="")
        tx -= 1
        ty -= 1

    tx = x + 1
    ty = y + 1
    while tx < 11 and ty < 9:
        print('[' + str(tx) + ',' + str(ty) + '] ', end="")
        tx += 1
        ty += 1

def allYX(x,y):
    tx = x - 1
    ty = y + 1
    while tx >= 2 and ty < 9:
        print('[' + str(tx) + ',' + str(ty) + '] ', end="")
        tx -= 1
        ty += 1

    tx = x + 1
    ty = y - 1
    while tx < 11 and ty >= 0:
        print('[' + str(tx) + ',' + str(ty) + '] ', end="")
        tx += 1
        ty -= 1





x = 2
y = 0

while x < 11:
    y = 0
    while y < 9:
        print('[' + str(x) + ',' + str(y) + ']-横:', end="")
        allX(x,y)
        print("")
        print('[' + str(x) + ',' + str(y) + ']-纵:', end="")
        allY(x,y)
        print("")
        print('[' + str(x) + ',' + str(y) + ']-正斜线:', end="")
        allXY(x,y)
        print("")
        print('[' + str(x) + ',' + str(y) + ']-反斜线:', end="")
        allYX(x,y)
        print("")
        y+=1
    x+=1



